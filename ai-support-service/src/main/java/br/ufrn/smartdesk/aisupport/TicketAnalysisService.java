package br.ufrn.smartdesk.aisupport;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class TicketAnalysisService {

	private final ConversationMemoryService conversationMemoryService;

	private final RagDocumentService ragDocumentService;

	private final SupportRulesMcpClient supportRulesMcpClient;

	private final SpringAiTicketTextClient springAiTicketTextClient;

	public TicketAnalysisService(
			ConversationMemoryService conversationMemoryService,
			RagDocumentService ragDocumentService,
			SupportRulesMcpClient supportRulesMcpClient,
			SpringAiTicketTextClient springAiTicketTextClient) {
		this.conversationMemoryService = conversationMemoryService;
		this.ragDocumentService = ragDocumentService;
		this.supportRulesMcpClient = supportRulesMcpClient;
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
		String memoryHint = conversationMemoryService.summaryHint(input.conversationId());

		String summary = summaryFor(category, ragGuidance, memoryHint);
		String suggestedAnswer = suggestedAnswerFor(category, ragGuidance, mcpRule.recommendation(), memoryHint);

		SpringAiTicketTextClient.TicketPromptContext promptContext = new SpringAiTicketTextClient.TicketPromptContext(
				description,
				category,
				priority,
				ragGuidance,
				mcpRule.recommendation(),
				memoryHint);

		SpringAiTicketTextClient.GeneratedText generatedText = springAiTicketTextClient.generate(promptContext)
				.orElse(new SpringAiTicketTextClient.GeneratedText(summary, suggestedAnswer));

		TicketAnalysis analysis = new TicketAnalysis(
				category,
				priority,
				generatedText.summary(),
				generatedText.suggestedAnswer(),
				ragContext.source(),
				mcpRule.ruleName());

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

	private String suggestedAnswerFor(String category, String ragGuidance, String mcpRecommendation, String memoryHint) {
		String baseAnswer = switch (category) {
			case "REDE" -> "Verifique a conexao, autenticacao e alcance da rede Wi-Fi.";
			case "HARDWARE" -> "Verifique cabos, energia, perifericos e reinicie o equipamento.";
			default -> "Seu chamado foi registrado para triagem.";
		};

		String mcpText = mcpRecommendation.isBlank() ? "" : " Regra MCP: " + mcpRecommendation;
		return baseAnswer + " Base local: " + ragGuidance + mcpText + memoryHint;
	}

	private String normalize(String value) {
		String lowerCase = value.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(lowerCase, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
	}
}
