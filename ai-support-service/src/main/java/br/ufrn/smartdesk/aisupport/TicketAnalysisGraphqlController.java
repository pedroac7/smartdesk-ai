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
	public br.ufrn.smartdesk.aisupport.TicketAnalysis analyzeTicket(
			@Argument br.ufrn.smartdesk.aisupport.AnalyzeTicketInput input) {
		return ticketAnalysisService.analyze(input);
	}
}
