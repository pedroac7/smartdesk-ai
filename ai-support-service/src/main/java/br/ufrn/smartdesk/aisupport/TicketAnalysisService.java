package br.ufrn.smartdesk.aisupport;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class TicketAnalysisService {

	private final ConversationMemoryService conversationMemoryService;

	private final RagDocumentService ragDocumentService;

	private final SupportRulesMcpClient supportRulesMcpClient;

	private final ExternalFilesystemMcpClient externalFilesystemMcpClient;

	private final SpringAiTicketTextClient springAiTicketTextClient;

	public TicketAnalysisService(
			ConversationMemoryService conversationMemoryService,
			RagDocumentService ragDocumentService,
			SupportRulesMcpClient supportRulesMcpClient,
			ExternalFilesystemMcpClient externalFilesystemMcpClient,
			SpringAiTicketTextClient springAiTicketTextClient) {
		this.conversationMemoryService = conversationMemoryService;
		this.ragDocumentService = ragDocumentService;
		this.supportRulesMcpClient = supportRulesMcpClient;
		this.externalFilesystemMcpClient = externalFilesystemMcpClient;
		this.springAiTicketTextClient = springAiTicketTextClient;
	}

	public TicketAnalysis analyze(AnalyzeTicketInput input) {
		String description = input.description() == null ? "" : input.description();
		String normalizedDescription = normalize(description);
		String category = classifyCategory(normalizedDescription);
		String priority = classifyPriority(normalizedDescription);
		RagDocumentService.RagContext ragContext = ragDocumentService.findContext(normalizedDescription, category);
		String ragGuidance = ragDocumentService.firstGuidanceLine(ragContext);
		SupportRulesMcpClient.SupportRuleResult mcpRule = supportRulesMcpClient.findRule(category, priority);
		ExternalFilesystemMcpClient.ExternalMcpResult externalMcp = externalFilesystemMcpClient.findAdvice(category,
				priority);
		String memoryHint = conversationMemoryService.summaryHint(input.conversationId());

		String summary = summaryFor(category, ragGuidance, memoryHint);
		String suggestedAnswer = suggestedAnswerFor(category, ragGuidance, mcpRule.recommendation(), externalMcp,
				memoryHint);

		SpringAiTicketTextClient.TicketPromptContext promptContext = new SpringAiTicketTextClient.TicketPromptContext(
				description,
				category,
				priority,
				ragGuidance,
				mcpRule.recommendation(),
				externalMcp.available() ? externalMcp.advice() : "",
				memoryHint);

		SpringAiTicketTextClient.GeneratedText generatedText = springAiTicketTextClient.generate(promptContext)
				.orElse(new SpringAiTicketTextClient.GeneratedText(summary, suggestedAnswer));
		String finalSuggestedAnswer = appendExternalAdvice(generatedText.suggestedAnswer(), externalMcp);

		TicketAnalysis analysis = new TicketAnalysis(
				category,
				priority,
				generatedText.summary(),
				finalSuggestedAnswer,
				ragContext.source(),
				mcpRule.ruleName(),
				externalMcp.toolUsed(),
				externalMcp.advice());

		conversationMemoryService.remember(input.conversationId(), description, analysis);
		return analysis;
	}

	private String classifyCategory(String description) {
		if (containsAny(description, "wifi", "wi-fi", "internet", "rede")) {
			return "REDE";
		}
		if (containsAny(description, "notebook", "computador", "teclado", "mouse", "monitor")) {
			return "HARDWARE";
		}
		return "SUPORTE_GERAL";
	}

	private String classifyPriority(String description) {
		if (containsAny(description, "urgente", "parado", "nao funciona", "indisponivel")) {
			return "ALTA";
		}
		return "MEDIA";
	}

	private boolean containsAny(String text, String... terms) {
		for (String term : terms) {
			if (text.contains(term)) {
				return true;
			}
		}
		return false;
	}

	private String summaryFor(String category, String ragGuidance, String memoryHint) {
		String baseSummary = switch (category) {
			case "REDE" -> "Problema relacionado a conectividade ou rede.";
			case "HARDWARE" -> "Problema relacionado a equipamento ou periferico.";
			default -> "Chamado geral de suporte.";
		};
		return baseSummary + " Contexto RAG: " + ragGuidance + memoryHint;
	}

	private String suggestedAnswerFor(String category, String ragGuidance, String mcpRecommendation,
			ExternalFilesystemMcpClient.ExternalMcpResult externalMcp, String memoryHint) {
		String baseAnswer = switch (category) {
			case "REDE" -> "Verifique a conexao, autenticacao e alcance da rede Wi-Fi.";
			case "HARDWARE" -> "Verifique cabos, energia, perifericos e reinicie o equipamento.";
			default -> "Seu chamado foi registrado para triagem.";
		};

		String mcpText = mcpRecommendation.isBlank() ? "" : " Regra MCP: " + mcpRecommendation;
		String externalMcpText = externalMcp.available() ? " Checklist externo: " + externalMcp.advice() : "";
		return baseAnswer + " Base local: " + ragGuidance + mcpText + externalMcpText + memoryHint;
	}

	private String appendExternalAdvice(String suggestedAnswer,
			ExternalFilesystemMcpClient.ExternalMcpResult externalMcp) {
		if (!externalMcp.available() || externalMcp.advice().isBlank()) {
			return suggestedAnswer;
		}
		if (suggestedAnswer.contains(externalMcp.advice())) {
			return suggestedAnswer;
		}
		return suggestedAnswer + " Checklist externo: " + externalMcp.advice();
	}

	private String normalize(String value) {
		String lowerCase = value.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(lowerCase, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
	}
}
