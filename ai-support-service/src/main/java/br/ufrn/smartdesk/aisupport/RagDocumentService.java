package br.ufrn.smartdesk.aisupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.springframework.stereotype.Service;

@Service
public class RagDocumentService {

	private final AiSupportProperties properties;

	public RagDocumentService(AiSupportProperties properties) {
		this.properties = properties;
	}

	public RagContext findContext(String normalizedDescription, String category) {
		String fileName = documentFileName(normalizedDescription, category);
		return new RagContext(displaySource(fileName), readDocument(fileName));
	}

	public String firstGuidanceLine(RagContext context) {
		return Arrays.stream(context.content().split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isBlank())
				.filter(line -> !line.startsWith("#"))
				.findFirst()
				.orElse("Procedimento local de suporte indisponivel.");
	}

	private String documentFileName(String normalizedDescription, String category) {
		if (containsAny(normalizedDescription, "wifi", "wi-fi", "internet", "rede") || "REDE".equals(category)) {
			return "rede.md";
		}
		if (containsAny(normalizedDescription, "notebook", "computador", "teclado", "mouse", "monitor")
				|| "HARDWARE".equals(category)) {
			return "hardware.md";
		}
		return "suporte-geral.md";
	}

	private String readDocument(String fileName) {
		Path docsDirectory = Path.of(properties.getRag().getDocsPath()).toAbsolutePath().normalize();
		Path document = docsDirectory.resolve(fileName).normalize();
		if (!document.startsWith(docsDirectory)) {
			return "Documento RAG rejeitado por caminho invalido.";
		}

		try {
			return Files.readString(document, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return "Documento RAG indisponivel: " + fileName;
		}
	}

	private String displaySource(String fileName) {
		Path docsPath = Path.of(properties.getRag().getDocsPath()).normalize();
		Path directoryName = docsPath.getFileName();
		String displayDirectory = directoryName == null ? "rag-docs" : directoryName.toString();
		return (displayDirectory + "/" + fileName).replace('\\', '/');
	}

	private boolean containsAny(String text, String... terms) {
		for (String term : terms) {
			if (text.contains(term)) {
				return true;
			}
		}
		return false;
	}

	public record RagContext(String source, String content) {
	}
}
