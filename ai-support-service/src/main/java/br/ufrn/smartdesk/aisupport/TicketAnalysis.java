package br.ufrn.smartdesk.aisupport;

public record TicketAnalysis(
		String category,
		String priority,
		String summary,
		String suggestedAnswer,
		String ragSource,
		String mcpRuleUsed,
		String externalMcpToolUsed,
		String externalMcpAdvice) {
}
