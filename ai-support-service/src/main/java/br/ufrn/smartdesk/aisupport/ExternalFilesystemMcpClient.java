package br.ufrn.smartdesk.aisupport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExternalFilesystemMcpClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExternalFilesystemMcpClient.class);

	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final AiSupportProperties properties;

	private final ObjectMapper objectMapper;

	private final ConcurrentMap<String, ExternalMcpDocument> documentCache = new ConcurrentHashMap<>();

	private final ExecutorService ioExecutor = Executors.newCachedThreadPool(new McpThreadFactory());

	public ExternalFilesystemMcpClient(AiSupportProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void warmUp() {
		AiSupportProperties.Filesystem config = properties.getExternalMcp().getFilesystem();
		if (!isConfigured(config)) {
			return;
		}

		CompletableFuture.runAsync(() -> {
			for (String category : List.of("REDE", "HARDWARE", "SUPORTE_GERAL")) {
				String fileName = fileNameFor(category);
				documentCache.computeIfAbsent(fileName, ignored -> loadDocument(config, category).orElse(null));
			}
		}, ioExecutor);
	}

	public ExternalMcpResult findAdvice(String category, String priority) {
		AiSupportProperties.Filesystem config = properties.getExternalMcp().getFilesystem();
		if (!isConfigured(config)) {
			return ExternalMcpResult.unavailable();
		}

		String fileName = fileNameFor(category);
		ExternalMcpDocument document = documentCache.get(fileName);
		if (document == null) {
			document = loadDocument(config, category).orElse(null);
			if (document == null) {
				return ExternalMcpResult.unavailable();
			}
			ExternalMcpDocument cachedDocument = documentCache.putIfAbsent(fileName, document);
			if (cachedDocument != null) {
				document = cachedDocument;
			}
		}

		String advice = summarizeChecklist(document.content(), priority);
		return new ExternalMcpResult(document.toolUsed(), advice, true);
	}

	private boolean isConfigured(AiSupportProperties.Filesystem config) {
		return config.isEnabled()
				&& StringUtils.hasText(config.getCommand())
				&& StringUtils.hasText(config.getPackageName())
				&& StringUtils.hasText(config.getDocsPath());
	}

	private Optional<ExternalMcpDocument> loadDocument(AiSupportProperties.Filesystem config, String category) {
		Path docsDirectory = Path.of(config.getDocsPath()).toAbsolutePath().normalize();
		Path document = docsDirectory.resolve(fileNameFor(category)).normalize();
		if (!document.startsWith(docsDirectory)) {
			LOGGER.warn("External MCP document rejected by path validation: {}", document);
			return Optional.empty();
		}

		Duration timeout = config.getTimeout() == null ? Duration.ofSeconds(5) : config.getTimeout();
		Instant deadline = Instant.now().plus(timeout);
		Process process = null;

		try {
			process = startServer(config, docsDirectory);
			drainStderr(process);

			try (BufferedWriter writer = new BufferedWriter(
					new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

				sendRequest(writer, 1, "initialize", initializeParams());
				readResponse(reader, 1, deadline);

				sendNotification(writer, "notifications/initialized");

				sendRequest(writer, 2, "tools/list", Map.of());
				Map<String, Object> toolsResponse = readResponse(reader, 2, deadline);
				String toolName = chooseReadTool(toolsResponse)
						.orElseThrow(() -> new IllegalStateException("No readable filesystem MCP tool found"));

				sendRequest(writer, 3, "tools/call", Map.of(
						"name", toolName,
						"arguments", Map.of("path", document.toString())));
				Map<String, Object> toolResponse = readResponse(reader, 3, deadline);
				String checklist = extractTextContent(toolResponse);
				if (!StringUtils.hasText(checklist)) {
					return Optional.empty();
				}

				return Optional.of(new ExternalMcpDocument("server-filesystem/" + toolName, checklist));
			}
		}
		catch (IOException | RuntimeException | TimeoutException ex) {
			LOGGER.warn("External filesystem MCP unavailable: {}", ex.getMessage());
			return Optional.empty();
		}
		finally {
			stopProcess(process);
		}
	}

	private Process startServer(AiSupportProperties.Filesystem config, Path docsDirectory) throws IOException {
		List<String> command = List.of(
				resolveCommand(config.getCommand()),
				"-y",
				config.getPackageName(),
				docsDirectory.toString());
		return new ProcessBuilder(command)
				.redirectError(ProcessBuilder.Redirect.PIPE)
				.start();
	}

	private String resolveCommand(String configuredCommand) {
		String command = configuredCommand.trim();
		if (Path.of(command).isAbsolute() || command.contains("/") || command.contains("\\")) {
			return command;
		}
		return findOnPath(command).orElseGet(() -> fallbackCommand(command));
	}

	private Optional<String> findOnPath(String command) {
		String path = System.getenv("PATH");
		if (!StringUtils.hasText(path)) {
			return Optional.empty();
		}

		for (String pathEntry : path.split(File.pathSeparator)) {
			if (!StringUtils.hasText(pathEntry)) {
				continue;
			}
			for (String candidateName : executableCandidateNames(command)) {
				Path candidate = Path.of(pathEntry, candidateName);
				if (Files.isRegularFile(candidate)) {
					return Optional.of(candidate.toString());
				}
			}
		}
		return Optional.empty();
	}

	private List<String> executableCandidateNames(String command) {
		if (!isWindows() || command.contains(".")) {
			return List.of(command);
		}

		String pathExt = System.getenv("PATHEXT");
		if (!StringUtils.hasText(pathExt)) {
			pathExt = ".COM;.EXE;.BAT;.CMD";
		}

		List<String> candidateNames = new ArrayList<>();
		for (String extension : pathExt.split(";")) {
			if (StringUtils.hasText(extension)) {
				candidateNames.add(command + extension.toLowerCase(Locale.ROOT));
				candidateNames.add(command + extension.toUpperCase(Locale.ROOT));
			}
		}
		candidateNames.add(command);
		return candidateNames;
	}

	private String fallbackCommand(String command) {
		if (isWindows() && !command.contains(".")) {
			return command + ".cmd";
		}
		return command;
	}

	private boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private Map<String, Object> initializeParams() {
		return Map.of(
				"protocolVersion", "2024-11-05",
				"capabilities", Map.of(),
				"clientInfo", Map.of(
						"name", "smartdesk-ai-support-service",
						"version", "0.0.1"));
	}

	private void sendRequest(BufferedWriter writer, int id, String method, Map<String, Object> params)
			throws IOException {
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("jsonrpc", "2.0");
		message.put("id", id);
		message.put("method", method);
		message.put("params", params);
		writeMessage(writer, message);
	}

	private void sendNotification(BufferedWriter writer, String method) throws IOException {
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("jsonrpc", "2.0");
		message.put("method", method);
		writeMessage(writer, message);
	}

	private void writeMessage(BufferedWriter writer, Map<String, Object> message) throws IOException {
		writer.write(objectMapper.writeValueAsString(message));
		writer.newLine();
		writer.flush();
	}

	private Map<String, Object> readResponse(BufferedReader reader, int expectedId, Instant deadline)
			throws TimeoutException {
		while (true) {
			String line = readLine(reader, deadline);
			if (!StringUtils.hasText(line)) {
				continue;
			}

			Map<String, Object> message = readJsonMessage(line);
			if (message.isEmpty() || !idMatches(message.get("id"), expectedId)) {
				continue;
			}

			if (message.containsKey("error")) {
				throw new IllegalStateException("MCP JSON-RPC error: " + message.get("error"));
			}
			return message;
		}
	}

	private String readLine(BufferedReader reader, Instant deadline) throws TimeoutException {
		try {
			CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
				try {
					return reader.readLine();
				}
				catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			}, ioExecutor);

			long remaining = remainingMillis(deadline);
			String line = future.get(remaining, TimeUnit.MILLISECONDS);
			if (line == null) {
				throw new IllegalStateException("External MCP process closed stdout");
			}
			return line;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while reading external MCP response", ex);
		}
		catch (ExecutionException ex) {
			throw new IllegalStateException("Failed to read external MCP response", ex.getCause());
		}
	}

	private long remainingMillis(Instant deadline) throws TimeoutException {
		long remaining = Duration.between(Instant.now(), deadline).toMillis();
		if (remaining <= 0) {
			throw new TimeoutException("External MCP call timed out");
		}
		return remaining;
	}

	private Map<String, Object> readJsonMessage(String line) {
		try {
			return objectMapper.readValue(line, MAP_TYPE);
		}
		catch (JsonProcessingException ex) {
			LOGGER.debug("Ignoring non-JSON external MCP stdout line: {}", line);
			return Map.of();
		}
	}

	private boolean idMatches(Object id, int expectedId) {
		if (id instanceof Number number) {
			return number.intValue() == expectedId;
		}
		return Integer.toString(expectedId).equals(String.valueOf(id));
	}

	private Optional<String> chooseReadTool(Map<String, Object> toolsResponse) {
		List<String> toolNames = toolNames(toolsResponse);
		for (String preferred : List.of("read_file", "read_text_file")) {
			if (toolNames.contains(preferred)) {
				return Optional.of(preferred);
			}
		}
		return toolNames.stream()
				.filter(this::looksLikeReadFileTool)
				.findFirst();
	}

	private List<String> toolNames(Map<String, Object> toolsResponse) {
		Object result = toolsResponse.get("result");
		if (!(result instanceof Map<?, ?> resultMap)) {
			return List.of();
		}
		Object tools = resultMap.get("tools");
		if (!(tools instanceof List<?> toolList)) {
			return List.of();
		}

		List<String> names = new ArrayList<>();
		for (Object tool : toolList) {
			if (tool instanceof Map<?, ?> toolMap) {
				Object name = toolMap.get("name");
				if (name instanceof String toolName && StringUtils.hasText(toolName)) {
					names.add(toolName);
				}
			}
		}
		return names;
	}

	private boolean looksLikeReadFileTool(String toolName) {
		String normalized = toolName.toLowerCase(Locale.ROOT);
		return normalized.contains("read") && (normalized.contains("file") || normalized.contains("text"));
	}

	private String extractTextContent(Map<String, Object> toolResponse) {
		Object result = toolResponse.get("result");
		if (!(result instanceof Map<?, ?> resultMap)) {
			return "";
		}
		if (Boolean.TRUE.equals(resultMap.get("isError"))) {
			return "";
		}

		Object content = resultMap.get("content");
		if (!(content instanceof List<?> contentItems)) {
			return "";
		}

		StringBuilder text = new StringBuilder();
		for (Object contentItem : contentItems) {
			if (contentItem instanceof Map<?, ?> contentMap) {
				Object itemText = contentMap.get("text");
				if (itemText instanceof String value && StringUtils.hasText(value)) {
					if (!text.isEmpty()) {
						text.append(System.lineSeparator());
					}
					text.append(value);
				}
			}
		}
		return text.toString();
	}

	private String summarizeChecklist(String content, String priority) {
		String provider = Arrays.stream(content.split("\\R"))
				.map(String::trim)
				.filter(line -> line.regionMatches(true, 0, "Fornecedor:", 0, "Fornecedor:".length()))
				.findFirst()
				.map(line -> line.substring("Fornecedor:".length()).trim())
				.filter(StringUtils::hasText)
				.orElse("ExternalSupportVendor");

		List<String> guidance = Arrays.stream(content.split("\\R"))
				.map(this::cleanChecklistLine)
				.filter(StringUtils::hasText)
				.filter(line -> !line.startsWith("#"))
				.filter(line -> !line.regionMatches(true, 0, "Fornecedor:", 0, "Fornecedor:".length()))
				.filter(line -> !line.regionMatches(true, 0, "Para chamados", 0, "Para chamados".length()))
				.filter(line -> shouldIncludePriorityLine(line, priority))
				.limit(3)
				.toList();

		String summary = provider + " recomenda: " + String.join(" ", guidance);
		return truncate(summary, 280);
	}

	private String cleanChecklistLine(String line) {
		String trimmed = line.trim();
		if (trimmed.startsWith("-")) {
			return trimmed.substring(1).trim();
		}
		return trimmed;
	}

	private boolean shouldIncludePriorityLine(String line, String priority) {
		if ("ALTA".equalsIgnoreCase(priority)) {
			return true;
		}
		return !line.toLowerCase(Locale.ROOT).contains("prioridade for alta");
	}

	private String truncate(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}

	private String fileNameFor(String category) {
		return switch (category) {
			case "REDE" -> "vendor-network-checklist.md";
			case "HARDWARE" -> "vendor-hardware-checklist.md";
			default -> "vendor-general-checklist.md";
		};
	}

	private void drainStderr(Process process) {
		CompletableFuture.runAsync(() -> {
			try (BufferedReader stderr = new BufferedReader(
					new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = stderr.readLine()) != null) {
					LOGGER.debug("External filesystem MCP stderr: {}", line);
				}
			}
			catch (IOException ex) {
				LOGGER.debug("Failed to drain external MCP stderr: {}", ex.getMessage());
			}
		}, ioExecutor);
	}

	private void stopProcess(Process process) {
		if (process == null) {
			return;
		}
		process.destroy();
		try {
			if (!process.waitFor(1, TimeUnit.SECONDS)) {
				process.destroyForcibly();
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
		}
	}

	public record ExternalMcpResult(String toolUsed, String advice, boolean available) {

		public static ExternalMcpResult unavailable() {
			return new ExternalMcpResult(
					"external-mcp-unavailable",
					"MCP externo indisponivel no momento.",
					false);
		}
	}

	private record ExternalMcpDocument(String toolUsed, String content) {
	}

	private static final class McpThreadFactory implements ThreadFactory {

		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "external-filesystem-mcp-io");
			thread.setDaemon(true);
			return thread;
		}
	}
}
