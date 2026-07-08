package br.ufrn.smartdesk.aisupport;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationMemoryService {

	private static final int MAX_TURNS = 5;

	private final ChatMemory chatMemory;

	private final ConcurrentMap<String, ArrayDeque<ConversationTurn>> conversations = new ConcurrentHashMap<>();

	public ConversationMemoryService(ChatMemory chatMemory) {
		this.chatMemory = chatMemory;
	}

	public List<ConversationTurn> recentTurns(String conversationId) {
		if (!StringUtils.hasText(conversationId)) {
			return List.of();
		}
		ArrayDeque<ConversationTurn> turns = conversations.get(conversationId);
		if (turns == null) {
			return List.of();
		}
		synchronized (turns) {
			return List.copyOf(turns);
		}
	}

	public String summaryHint(String conversationId) {
		List<ConversationTurn> turns = recentTurns(conversationId);
		if (turns.isEmpty()) {
			return "";
		}
		ConversationTurn lastTurn = turns.get(turns.size() - 1);
		return " Historico recente: conversa anterior classificada como "
				+ lastTurn.category()
				+ " com prioridade "
				+ lastTurn.priority()
				+ ".";
	}

	public void remember(String conversationId, String description, TicketAnalysis analysis) {
		if (!StringUtils.hasText(conversationId)) {
			return;
		}

		List<Message> messages = List.of(
				new UserMessage(nullToEmpty(description)),
				new AssistantMessage(analysis.summary() + " " + analysis.suggestedAnswer()));
		chatMemory.add(conversationId, messages);

		ArrayDeque<ConversationTurn> turns = conversations.computeIfAbsent(conversationId, key -> new ArrayDeque<>());
		synchronized (turns) {
			turns.addLast(new ConversationTurn(
					nullToEmpty(description),
					analysis.category(),
					analysis.priority(),
					analysis.summary(),
					analysis.suggestedAnswer()));
			while (turns.size() > MAX_TURNS) {
				turns.removeFirst();
			}
		}
	}

	public List<String> springAiMemorySnapshot(String conversationId) {
		if (!StringUtils.hasText(conversationId)) {
			return List.of();
		}
		return chatMemory.get(conversationId)
				.stream()
				.map(this::messageText)
				.toList();
	}

	private String messageText(Message message) {
		if (message instanceof AbstractMessage abstractMessage) {
			return abstractMessage.getText();
		}
		return message.toString();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	public record ConversationTurn(
			String description,
			String category,
			String priority,
			String summary,
			String suggestedAnswer) {
	}
}
