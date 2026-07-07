package br.ufrn.smartdesk.ticketorchestrator;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TicketAnalysisController {

	private final TicketAnalysisService ticketAnalysisService;

	public TicketAnalysisController(TicketAnalysisService ticketAnalysisService) {
		this.ticketAnalysisService = ticketAnalysisService;
	}

	@PostMapping("/tickets/analyze")
	public TicketAnalyzeResponse analyze(@RequestBody TicketAnalyzeRequest request) {
		return ticketAnalysisService.analyze(request);
	}

	public record TicketAnalyzeRequest(String conversationId, String description) {
	}

	public record TicketAnalyzeResponse(
			String category,
			String priority,
			String summary,
			String suggestedAnswer,
			int slaHours,
			String supportTeam,
			String mode) {
	}
}
