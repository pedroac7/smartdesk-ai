package br.ufrn.smartdesk.ticketorchestrator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigDemoController {

	private static final String SERVICE_NAME = "ticket-orchestrator-service";
	private static final String CONFIG_SOURCE = "config-server";

	private final FallbackPolicyProvider fallbackPolicyProvider;

	public ConfigDemoController(FallbackPolicyProvider fallbackPolicyProvider) {
		this.fallbackPolicyProvider = fallbackPolicyProvider;
	}

	@GetMapping("/tickets/config-demo")
	public ConfigDemoResponse configDemo() {
		return new ConfigDemoResponse(
				SERVICE_NAME,
				CONFIG_SOURCE,
				fallbackPolicyProvider.current());
	}

	@PostMapping("/tickets/config-demo/reload")
	public ResponseEntity<?> reloadConfigDemo() {
		try {
			FallbackPolicyProvider.ReloadResult result = fallbackPolicyProvider.reload();
			return ResponseEntity.ok(new ConfigDemoReloadResponse(
					SERVICE_NAME,
					result.source(),
					result.oldPolicy(),
					result.newPolicy(),
					"Fallback policy reloaded from Config Server."));
		}
		catch (RuntimeException exception) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
					.body(new ConfigDemoReloadErrorResponse(
							SERVICE_NAME,
							"CONFIG_RELOAD_FAILED",
							exception.getMessage(),
							fallbackPolicyProvider.current(),
							CONFIG_SOURCE));
		}
	}

	public record ConfigDemoResponse(
			String service,
			String source,
			FallbackPolicyProvider.FallbackPolicy fallback) {
	}

	public record ConfigDemoReloadResponse(
			String service,
			String source,
			FallbackPolicyProvider.FallbackPolicy oldFallback,
			FallbackPolicyProvider.FallbackPolicy newFallback,
			String message) {
	}

	public record ConfigDemoReloadErrorResponse(
			String service,
			String error,
			String message,
			FallbackPolicyProvider.FallbackPolicy currentFallback,
			String source) {
	}
}
