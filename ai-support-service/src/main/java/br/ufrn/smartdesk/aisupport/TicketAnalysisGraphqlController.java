package br.ufrn.smartdesk.aisupport;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class TicketAnalysisGraphqlController {

	private final TicketAnalysisService ticketAnalysisService;

	public TicketAnalysisGraphqlController(TicketAnalysisService ticketAnalysisService) {
		this.ticketAnalysisService = ticketAnalysisService;
	}

	@QueryMapping
	public TicketAnalysis analyzeTicket(@Argument("input") AnalyzeTicketInput input) {
		return ticketAnalysisService.analyze(input);
	}
}
