package br.ufrn.smartdesk.ticketorchestrator;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import br.ufrn.smartdesk.ticketorchestrator.TicketAnalysisController.TicketAnalyzeRequest;
import br.ufrn.smartdesk.ticketorchestrator.TicketAnalysisController.TicketAnalyzeResponse;

@Service
public class TicketAnalysisService {

	private final AiSupportClient aiSupportClient;
	private final WebClient slaFunctionClient;
	private final FallbackPolicyProvider fallbackPolicyProvider;

	public TicketAnalysisService(AiSupportClient aiSupportClient,
			SmartdeskWebClientFactory webClientFactory,
			SmartdeskServicesProperties properties,
			FallbackPolicyProvider fallbackPolicyProvider) {
		this.aiSupportClient = aiSupportClient;
		this.slaFunctionClient = webClientFactory.create(properties.slaFunction().baseUrl());
		this.fallbackPolicyProvider = fallbackPolicyProvider;
	}

	public TicketAnalyzeResponse analyze(TicketAnalyzeRequest request) {
		AiTicketAnalysis analysis = aiSupportClient.analyze(request).join();

		if (analysis.isFallback()) {
			return fallbackResponse(analysis);
		}

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

	private TicketAnalyzeResponse fallbackResponse(AiTicketAnalysis analysis) {
		FallbackPolicyProvider.FallbackPolicy fallback = fallbackPolicyProvider.current();
		return new TicketAnalyzeResponse(
				fallback.category(),
				fallback.priority(),
				fallback.summary(),
				fallback.suggestedAnswer(),
				fallback.slaHours(),
				fallback.supportTeam(),
				"FALLBACK");
	}

	private SlaResponse calculateSla(AiTicketAnalysis analysis) {
		SlaRequest request = new SlaRequest(analysis.category(), analysis.priority());

		return slaFunctionClient.post()
				.uri("/calculateSla")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(SlaResponse.class)
				.block();
	}

	private record SlaRequest(String category, String priority) {
	}

	private record SlaResponse(int slaHours, String supportTeam) {
	}
}
