package br.ufrn.smartdesk.aisupport;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class TicketAnalysisGraphqlController {

	@QueryMapping
	public TicketAnalysis analyzeTicket(@Argument AnalyzeTicketInput input) {
		String normalizedDescription = normalize(input.description());
		String category = classifyCategory(normalizedDescription);
		String priority = classifyPriority(normalizedDescription);

		return new TicketAnalysis(
				category,
				priority,
				summaryFor(category),
				suggestedAnswerFor(category),
				"fake-rag-disabled",
				"fake-mcp-disabled");
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
		if (containsAny(description, "urgente", "parado", "nao funciona", "não funciona", "indisponivel")) {
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

	private String summaryFor(String category) {
		return switch (category) {
			case "REDE" -> "Problema relacionado a conectividade ou rede.";
			case "HARDWARE" -> "Problema relacionado a equipamento ou periferico.";
			default -> "Chamado geral de suporte.";
		};
	}

	private String suggestedAnswerFor(String category) {
		return switch (category) {
			case "REDE" -> "Verifique a conexao e tente reconectar a rede.";
			case "HARDWARE" -> "Verifique cabos, energia e reinicie o equipamento.";
			default -> "Seu chamado foi registrado para triagem.";
		};
	}

	private String normalize(String value) {
		String lowerCase = value.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(lowerCase, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
	}

	public record AnalyzeTicketInput(String conversationId, String description) {
	}

	public record TicketAnalysis(
			String category,
			String priority,
			String summary,
			String suggestedAnswer,
			String ragSource,
			String mcpRuleUsed) {
	}
}
