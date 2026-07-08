package br.ufrn.smartdesk.ticketorchestrator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import br.ufrn.smartdesk.ticketorchestrator.TicketAnalysisController.TicketAnalyzeRequest;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import reactor.core.publisher.Mono;

@Service
public class AiSupportClient {

	private static final String AI_SUPPORT_SERVICE = "aiSupportService";

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

	public AiSupportClient(WebClient.Builder loadBalancedWebClientBuilder,
			SmartdeskServicesProperties properties) {
		this.aiSupportClient = loadBalancedWebClientBuilder.clone()
				.baseUrl(properties.aiSupport().baseUrl())
				.build();
	}

	@CircuitBreaker(name = AI_SUPPORT_SERVICE, fallbackMethod = "fallbackAnalyze")
	@Retry(name = AI_SUPPORT_SERVICE, fallbackMethod = "fallbackAnalyze")
	@TimeLimiter(name = AI_SUPPORT_SERVICE, fallbackMethod = "fallbackAnalyze")
	@Bulkhead(name = AI_SUPPORT_SERVICE, fallbackMethod = "fallbackAnalyze")
	public CompletableFuture<AiTicketAnalysis> analyze(TicketAnalyzeRequest request) {
		GraphQlRequest graphQlRequest = new GraphQlRequest(
				ANALYZE_TICKET_QUERY,
				Map.of("input", request));

		return aiSupportClient.post()
				.uri("/graphql")
				.bodyValue(graphQlRequest)
				.retrieve()
				.bodyToMono(GraphQlResponse.class)
				.switchIfEmpty(Mono.error(new IllegalStateException("AI support service returned an empty response")))
				.map(this::extractAnalysis)
				.toFuture();
	}

	private AiTicketAnalysis extractAnalysis(GraphQlResponse response) {
		if (response.data() == null || response.data().analyzeTicket() == null) {
			throw new IllegalStateException("AI support service returned an empty GraphQL response");
		}
		if (response.errors() != null && !response.errors().isEmpty()) {
			throw new IllegalStateException("AI support service returned GraphQL errors");
		}
		return response.data().analyzeTicket();
	}

	private CompletableFuture<AiTicketAnalysis> fallbackAnalyze(TicketAnalyzeRequest request, Throwable throwable) {
		return CompletableFuture.completedFuture(AiTicketAnalysis.fallback());
	}

	private record GraphQlRequest(String query, Map<String, Object> variables) {
	}

	private record GraphQlResponse(GraphQlData data, List<GraphQlError> errors) {
	}

	private record GraphQlData(AiTicketAnalysis analyzeTicket) {
	}

	private record GraphQlError(String message) {
	}
}
