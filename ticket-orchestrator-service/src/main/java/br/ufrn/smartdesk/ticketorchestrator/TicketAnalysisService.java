package br.ufrn.smartdesk.ticketorchestrator;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import br.ufrn.smartdesk.ticketorchestrator.TicketAnalysisController.TicketAnalyzeRequest;
import br.ufrn.smartdesk.ticketorchestrator.TicketAnalysisController.TicketAnalyzeResponse;

@Service
public class TicketAnalysisService {

	private static final String ANALYZE_TICKET_QUERY = """
			query AnalyzeTicket($input: AnalyzeTicketInput!) {
			  analyzeTicket(input: $input) {
			    category
			    priority
			    summary
			    suggestedAnswer
			    ragSource
			    mcpRuleUsed
			  }
			}
			""";

	private final WebClient aiSupportClient;
	private final WebClient slaFunctionClient;

	public TicketAnalysisService(WebClient.Builder loadBalancedWebClientBuilder,
			SmartdeskServicesProperties properties) {
		this.aiSupportClient = loadBalancedWebClientBuilder.clone()
				.baseUrl(properties.aiSupport().baseUrl())
				.build();
		this.slaFunctionClient = loadBalancedWebClientBuilder.clone()
				.baseUrl(properties.slaFunction().baseUrl())
				.build();
	}

	public TicketAnalyzeResponse analyze(TicketAnalyzeRequest request) {
		TicketAnalysis analysis = analyzeWithFakeAi(request);
		SlaResponse sla = calculateSla(analysis);

		return new TicketAnalyzeResponse(
				analysis.category(),
				analysis.priority(),
				analysis.summary(),
				analysis.suggestedAnswer(),
				sla.slaHours(),
				sla.supportTeam(),
				"FAKE_AI");
	}

	private TicketAnalysis analyzeWithFakeAi(TicketAnalyzeRequest request) {
		GraphQlRequest graphQlRequest = new GraphQlRequest(
				ANALYZE_TICKET_QUERY,
				Map.of("input", request));

		GraphQlResponse response = aiSupportClient.post()
				.uri("/graphql")
				.bodyValue(graphQlRequest)
				.retrieve()
				.bodyToMono(GraphQlResponse.class)
				.block();

		if (response == null || response.data() == null || response.data().analyzeTicket() == null) {
			throw new IllegalStateException("AI support service returned an empty GraphQL response");
		}
		if (response.errors() != null && !response.errors().isEmpty()) {
			throw new IllegalStateException("AI support service returned GraphQL errors");
		}
		return response.data().analyzeTicket();
	}

	private SlaResponse calculateSla(TicketAnalysis analysis) {
		SlaRequest request = new SlaRequest(analysis.category(), analysis.priority());

		return slaFunctionClient.post()
				.uri("/calculateSla")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(SlaResponse.class)
				.block();
	}

	private record GraphQlRequest(String query, Map<String, Object> variables) {
	}

	private record GraphQlResponse(GraphQlData data, List<GraphQlError> errors) {
	}

	private record GraphQlData(TicketAnalysis analyzeTicket) {
	}

	private record GraphQlError(String message) {
	}

	private record TicketAnalysis(
			String category,
			String priority,
			String summary,
			String suggestedAnswer,
			String ragSource,
			String mcpRuleUsed) {
	}

	private record SlaRequest(String category, String priority) {
	}

	private record SlaResponse(int slaHours, String supportTeam) {
	}
}
