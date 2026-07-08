package br.ufrn.smartdesk.aisupport;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class SupportRulesMcpClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(SupportRulesMcpClient.class);

	private final RestClient.Builder restClientBuilder;

	private final RestClient.Builder loadBalancedRestClientBuilder;

	private final String baseUrl;

	public SupportRulesMcpClient(
			@Qualifier("restClientBuilder") RestClient.Builder restClientBuilder,
			@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder loadBalancedRestClientBuilder,
			AiSupportProperties properties) {
		this.restClientBuilder = restClientBuilder;
		this.loadBalancedRestClientBuilder = loadBalancedRestClientBuilder;
		this.baseUrl = trimTrailingSlash(properties.getServices().getSupportRulesMcp().getBaseUrl());
	}

	public SupportRuleResult findRule(String category, String priority) {
		if (!StringUtils.hasText(baseUrl)) {
			return SupportRuleResult.unavailable();
		}

		try {
			RestClient restClient = restClientBuilderFor(baseUrl).clone()
					.baseUrl(restClientBaseUrl(baseUrl))
					.build();

			SupportRuleResponse response = restClient.post()
					.uri("/mcp/tools/support-rule")
					.body(new SupportRuleRequest(category, priority))
					.retrieve()
					.body(SupportRuleResponse.class);

			if (response == null || !StringUtils.hasText(response.ruleName())) {
				return SupportRuleResult.unavailable();
			}

			return new SupportRuleResult(response.ruleName(), response.recommendation(), true);
		}
		catch (RuntimeException ex) {
			LOGGER.warn("Support rules MCP server unavailable: {}", ex.getMessage());
			return SupportRuleResult.unavailable();
		}
	}

	private RestClient.Builder restClientBuilderFor(String url) {
		if (shouldUseLoadBalancer(url)) {
			return loadBalancedRestClientBuilder;
		}
		return restClientBuilder;
	}

	private boolean shouldUseLoadBalancer(String url) {
		if (isAbsoluteHttpUrl(url)) {
			return false;
		}
		if (url.startsWith("lb://")) {
			return true;
		}
		try {
			String host = URI.create(url).getHost();
			if (!StringUtils.hasText(host)) {
				return StringUtils.hasText(url);
			}
			return !isLocalHost(host) && !isIpAddress(host) && !host.contains(".");
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private String restClientBaseUrl(String url) {
		if (url.startsWith("lb://")) {
			return "http://" + url.substring("lb://".length());
		}
		if (isAbsoluteHttpUrl(url)) {
			return url;
		}
		return "http://" + url;
	}

	private boolean isAbsoluteHttpUrl(String url) {
		return url.startsWith("http://") || url.startsWith("https://");
	}

	private boolean isLocalHost(String host) {
		return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
	}

	private boolean isIpAddress(String host) {
		return host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
	}

	private String trimTrailingSlash(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	public record SupportRuleRequest(String category, String priority) {
	}

	public record SupportRuleResponse(String tool, String ruleName, String recommendation) {
	}

	public record SupportRuleResult(String ruleName, String recommendation, boolean available) {

		public static SupportRuleResult unavailable() {
			return new SupportRuleResult("mcp-unavailable", "", false);
		}
	}
}
