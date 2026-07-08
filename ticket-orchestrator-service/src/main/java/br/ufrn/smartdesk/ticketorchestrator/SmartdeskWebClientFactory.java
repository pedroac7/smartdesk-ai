package br.ufrn.smartdesk.ticketorchestrator;

import java.net.URI;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SmartdeskWebClientFactory {

	private final WebClient.Builder webClientBuilder;

	private final WebClient.Builder loadBalancedWebClientBuilder;

	public SmartdeskWebClientFactory(
			@Qualifier("webClientBuilder") WebClient.Builder webClientBuilder,
			@Qualifier("loadBalancedWebClientBuilder") WebClient.Builder loadBalancedWebClientBuilder) {
		this.webClientBuilder = webClientBuilder;
		this.loadBalancedWebClientBuilder = loadBalancedWebClientBuilder;
	}

	public WebClient create(String baseUrl) {
		return builderFor(baseUrl)
				.clone()
				.baseUrl(baseUrl)
				.build();
	}

	private WebClient.Builder builderFor(String baseUrl) {
		if (shouldUseLoadBalancer(baseUrl)) {
			return loadBalancedWebClientBuilder;
		}
		return webClientBuilder;
	}

	private boolean shouldUseLoadBalancer(String baseUrl) {
		try {
			String host = URI.create(baseUrl).getHost();
			if (!StringUtils.hasText(host)) {
				return false;
			}
			return !isLocalHost(host) && !isIpAddress(host) && !host.contains(".");
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private boolean isLocalHost(String host) {
		return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
	}

	private boolean isIpAddress(String host) {
		return host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
	}
}
