package br.ufrn.smartdesk.aisupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class SupportRulesMcpClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(SupportRulesMcpClient.class);

	private final RestClient.Builder loadBalancedRestClientBuilder;

	private final String baseUrl;

	public SupportRulesMcpClient(RestClient.Builder loadBalancedRestClientBuilder, AiSupportProperties properties) {
		this.loadBalancedRestClientBuilder = loadBalancedRestClientBuilder;
		this.baseUrl = trimTrailingSlash(properties.getServices().getSupportRulesMcp().getBaseUrl());
	}

	public SupportRuleResult findRule(String category, String priority) {
		if (!StringUtils.hasText(baseUrl)) {
			return SupportRuleResult.unavailable();
		}

		try {
			RestClient restClient = loadBalancedRestClientBuilder.clone()
					.baseUrl(baseUrl)
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
		catch (RestClientException ex) {
			LOGGER.warn("Support rules MCP server unavailable: {}", ex.getMessage());
			return SupportRuleResult.unavailable();
		}
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
