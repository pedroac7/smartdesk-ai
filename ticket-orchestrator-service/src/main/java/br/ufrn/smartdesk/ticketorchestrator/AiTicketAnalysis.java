package br.ufrn.smartdesk.ticketorchestrator;

public record AiTicketAnalysis(
		String category,
		String priority,
		String summary,
		String suggestedAnswer,
		String ragSource,
		String mcpRuleUsed,
		String aiProvider,
		boolean realAiUsed,
		String mode) {

	private static final String FALLBACK_MODE = "FALLBACK";

	public static AiTicketAnalysis fallback() {
		return new AiTicketAnalysis(
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				false,
				FALLBACK_MODE);
	}

	public boolean isFallback() {
		return FALLBACK_MODE.equals(mode);
	}
}
