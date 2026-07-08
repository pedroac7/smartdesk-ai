package br.ufrn.smartdesk.ticketorchestrator;

public record AiTicketAnalysis(
		String category,
		String priority,
		String summary,
		String suggestedAnswer,
		String ragSource,
		String mcpRuleUsed,
		String mode) {

	private static final String FALLBACK_MODE = "FALLBACK";

	public static AiTicketAnalysis fallback() {
		return new AiTicketAnalysis(
				"SUPORTE_GERAL",
				"MEDIA",
				"Análise automática indisponível no momento.",
				"Seu chamado foi registrado e será analisado por um atendente.",
				null,
				null,
				FALLBACK_MODE);
	}

	public boolean isFallback() {
		return FALLBACK_MODE.equals(mode);
	}
}
