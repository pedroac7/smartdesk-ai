package br.ufrn.smartdesk.aisupport;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class SpringAiTicketTextClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiTicketTextClient.class);

	private final AiSupportProperties properties;

	private final RestClient.Builder restClientBuilder;

	public SpringAiTicketTextClient(AiSupportProperties properties,
			@Qualifier("restClientBuilder") RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.restClientBuilder = restClientBuilder;
	}

	public Optional<GeneratedText> generate(TicketPromptContext context) {
		if (properties.getAi().isGeminiMode()) {
			return generateWithGemini(context);
		}
		if (properties.getAi().isOpenaiMode()) {
			return generateWithOpenAi(context);
		}
		return Optional.empty();
	}

	private Optional<GeneratedText> generateWithOpenAi(TicketPromptContext context) {
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			LOGGER.info("smartdesk.ai.mode=openai, but OPENAI_API_KEY is not defined. Using local fake AI.");
			return Optional.empty();
		}
		if (!StringUtils.hasText(properties.getAi().getOpenai().getModel())) {
			LOGGER.info("smartdesk.ai.mode=openai, but smartdesk.ai.openai.model is not configured. Using local fake AI.");
			return Optional.empty();
		}

		try {
			ChatModel chatModel = createChatModel(apiKey);
			String response = chatModel.call(promptFor(context));
			return parseResponse(response, "OPENAI", true);
		}
		catch (RuntimeException ex) {
			LOGGER.warn("Spring AI OpenAI call failed. Using local fake AI. Cause: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private Optional<GeneratedText> generateWithGemini(TicketPromptContext context) {
		String apiKey = System.getenv("GEMINI_API_KEY");
		AiSupportProperties.Gemini gemini = properties.getAi().getGemini();
		if (!StringUtils.hasText(apiKey)) {
			LOGGER.info("smartdesk.ai.mode=gemini, but GEMINI_API_KEY is not defined. Using local fake AI.");
			return Optional.empty();
		}
		if (!StringUtils.hasText(gemini.getBaseUrl()) || !StringUtils.hasText(gemini.getModel())) {
			LOGGER.info("smartdesk.ai.mode=gemini, but Gemini base URL or model is not configured. Using local fake AI.");
			return Optional.empty();
		}

		try {
			RestClient geminiClient = createGeminiClient(gemini);
			GeminiChatCompletionResponse response = geminiClient.post()
					.uri("/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.header("Authorization", "Bearer " + apiKey)
					.body(new GeminiChatCompletionRequest(
							gemini.getModel(),
							List.of(
									new GeminiMessage("system", systemPrompt()),
									new GeminiMessage("user", promptFor(context))),
							0.2))
					.retrieve()
					.body(GeminiChatCompletionResponse.class);

			return parseResponse(extractGeminiContent(response), "GEMINI", true);
		}
		catch (RuntimeException ex) {
			LOGGER.warn("Gemini call failed. Using local fake AI. Cause: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private RestClient createGeminiClient(AiSupportProperties.Gemini gemini) {
		Duration timeout = gemini.getTimeout() == null ? Duration.ofSeconds(10) : gemini.getTimeout();
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(timeout)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return restClientBuilder.clone()
				.baseUrl(removeTrailingSlash(gemini.getBaseUrl()))
				.requestFactory(requestFactory)
				.build();
	}

	private ChatModel createChatModel(String apiKey) {
		OpenAIClient openAIClient = new OpenAIClientImpl(ClientOptions.builder()
				.apiKey(apiKey)
				.build());

		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.apiKey(apiKey)
				.model(properties.getAi().getOpenai().getModel())
				.temperature(0.2)
				.maxTokens(220)
				.build();

		return OpenAiChatModel.builder()
				.openAiClient(openAIClient)
				.options(options)
				.build();
	}

	private String promptFor(TicketPromptContext context) {
		return """
				Responda apenas neste formato:
				SUMMARY: texto curto
				SUGGESTED_ANSWER: texto curto

				Categoria: %s
				Prioridade: %s
				Descricao: %s
				Contexto RAG: %s
				Regra MCP: %s
				Checklist MCP externo: %s
				Historico: %s
				""".formatted(
				context.category(),
				context.priority(),
				context.description(),
				context.ragGuidance(),
				context.mcpRecommendation(),
				context.externalMcpAdvice(),
				context.memoryHint());
	}

	private String systemPrompt() {
		return """
				Voce e um assistente de suporte academico.
				Use a categoria, prioridade, contexto RAG, regra MCP, conselho MCP externo e historico.
				Responda somente no formato solicitado pelo usuario.
				Nao invente procedimentos fora do contexto recebido.
				""";
	}

	private Optional<GeneratedText> parseResponse(String response, String aiProvider, boolean realAiUsed) {
		if (!StringUtils.hasText(response)) {
			return Optional.empty();
		}

		String summary = "";
		String suggestedAnswer = "";
		for (String line : response.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.regionMatches(true, 0, "SUMMARY:", 0, "SUMMARY:".length())) {
				summary = trimmed.substring("SUMMARY:".length()).trim();
			}
			if (trimmed.regionMatches(true, 0, "SUGGESTED_ANSWER:", 0, "SUGGESTED_ANSWER:".length())) {
				suggestedAnswer = trimmed.substring("SUGGESTED_ANSWER:".length()).trim();
			}
		}

		if (!StringUtils.hasText(summary) || !StringUtils.hasText(suggestedAnswer)) {
			return Optional.empty();
		}
		return Optional.of(new GeneratedText(summary, suggestedAnswer, aiProvider, realAiUsed));
	}

	private String extractGeminiContent(GeminiChatCompletionResponse response) {
		if (response == null || response.choices() == null || response.choices().isEmpty()) {
			return "";
		}
		GeminiChoice firstChoice = response.choices().getFirst();
		if (firstChoice.message() == null) {
			return "";
		}
		return firstChoice.message().content();
	}

	private String removeTrailingSlash(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	public record TicketPromptContext(
			String description,
			String category,
			String priority,
			String ragGuidance,
			String mcpRecommendation,
			String externalMcpAdvice,
			String memoryHint) {
	}

	public record GeneratedText(String summary, String suggestedAnswer, String aiProvider, boolean realAiUsed) {
	}

	private record GeminiChatCompletionRequest(String model, List<GeminiMessage> messages, double temperature) {
	}

	private record GeminiMessage(String role, String content) {
	}

	private record GeminiChatCompletionResponse(List<GeminiChoice> choices) {
	}

	private record GeminiChoice(GeminiMessage message) {
	}
}
