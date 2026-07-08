package br.ufrn.smartdesk.aisupport;

import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SpringAiTicketTextClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiTicketTextClient.class);

	private final AiSupportProperties properties;

	public SpringAiTicketTextClient(AiSupportProperties properties) {
		this.properties = properties;
	}

	public Optional<GeneratedText> generate(TicketPromptContext context) {
		if (!properties.getAi().isOpenaiMode()) {
			return Optional.empty();
		}

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
			return parseResponse(response);
		}
		catch (RuntimeException ex) {
			LOGGER.warn("Spring AI OpenAI call failed. Using local fake AI. Cause: {}", ex.getMessage());
			return Optional.empty();
		}
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
				Voce e um assistente de suporte academico.
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

	private Optional<GeneratedText> parseResponse(String response) {
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
		return Optional.of(new GeneratedText(summary, suggestedAnswer));
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

	public record GeneratedText(String summary, String suggestedAnswer) {
	}
}
