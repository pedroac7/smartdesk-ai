package br.ufrn.smartdesk.slafunction;

import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlaFunctionConfiguration {

	@Bean
	public Function<SlaRequest, SlaResponse> calculateSla() {
		return request -> {
			String supportTeam = supportTeamFor(request.category());
			int slaHours = slaHoursFor(request.category(), request.priority());

			return new SlaResponse(slaHours, supportTeam);
		};
	}

	private int slaHoursFor(String category, String priority) {
		if ("ALTA".equalsIgnoreCase(priority)) {
			return 4;
		}
		if ("REDE".equalsIgnoreCase(category) && "MEDIA".equalsIgnoreCase(priority)) {
			return 8;
		}
		if ("HARDWARE".equalsIgnoreCase(category) && "MEDIA".equalsIgnoreCase(priority)) {
			return 12;
		}
		return 24;
	}

	private String supportTeamFor(String category) {
		if ("REDE".equalsIgnoreCase(category)) {
			return "Suporte de Redes";
		}
		if ("HARDWARE".equalsIgnoreCase(category)) {
			return "Suporte de Hardware";
		}
		return "Triagem Manual";
	}

	public record SlaRequest(String category, String priority) {
	}

	public record SlaResponse(int slaHours, String supportTeam) {
	}
}
