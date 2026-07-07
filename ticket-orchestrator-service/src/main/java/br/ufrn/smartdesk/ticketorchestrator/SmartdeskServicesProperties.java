package br.ufrn.smartdesk.ticketorchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartdesk.services")
public record SmartdeskServicesProperties(ServiceEndpoint aiSupport, ServiceEndpoint slaFunction) {

	public record ServiceEndpoint(String baseUrl) {
	}
}
