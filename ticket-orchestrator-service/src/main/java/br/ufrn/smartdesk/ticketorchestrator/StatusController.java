package br.ufrn.smartdesk.ticketorchestrator;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

	@GetMapping("/tickets/status")
	public StatusResponse status() {
		return new StatusResponse(
				"ticket-orchestrator-service",
				"UP",
				"Ticket Orchestrator disponível");
	}

	public record StatusResponse(String service, String status, String message) {
	}
}
