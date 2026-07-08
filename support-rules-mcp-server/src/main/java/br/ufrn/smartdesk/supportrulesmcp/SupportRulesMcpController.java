package br.ufrn.smartdesk.supportrulesmcp;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mcp")
public class SupportRulesMcpController {

	@GetMapping("/status")
	public McpStatus status() {
		return new McpStatus("support-rules-mcp-server", "UP");
	}

	@GetMapping("/tools")
	public List<McpTool> tools() {
		return List.of(new McpTool(
				"support-rule",
				"Retorna regra externa de triagem por categoria e prioridade."));
	}

	@PostMapping("/tools/support-rule")
	public SupportRuleResponse supportRule(@Valid @RequestBody SupportRuleRequest request) {
		String category = normalizeValue(request.category());
		String priority = normalizeValue(request.priority());
		String ruleName = ruleNameFor(category);
		String recommendation = recommendationFor(category);

		if ("ALTA".equals(priority)) {
			recommendation = recommendation + " Prioridade critica: acionar equipe responsavel imediatamente.";
		}

		return new SupportRuleResponse("support-rule", ruleName, recommendation);
	}

	private String ruleNameFor(String category) {
		return switch (category) {
			case "REDE" -> "NETWORK_STANDARD_TRIAGE";
			case "HARDWARE" -> "HARDWARE_STANDARD_TRIAGE";
			default -> "GENERAL_SUPPORT_TRIAGE";
		};
	}

	private String recommendationFor(String category) {
		return switch (category) {
			case "REDE" -> "Verificar conectividade, autenticacao e alcance do roteador.";
			case "HARDWARE" -> "Verificar energia, cabos, perifericos e estado fisico do equipamento.";
			default -> "Coletar mais detalhes, validar impacto e encaminhar para triagem manual.";
		};
	}

	private String normalizeValue(String value) {
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return normalized.toUpperCase(Locale.ROOT);
	}

	public record McpStatus(String service, String status) {
	}

	public record McpTool(String name, String description) {
	}

	public record SupportRuleRequest(
			@NotBlank String category,
			@NotBlank String priority) {
	}

	public record SupportRuleResponse(String tool, String ruleName, String recommendation) {
	}
}
