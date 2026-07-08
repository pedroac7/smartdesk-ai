package br.ufrn.smartdesk.ticketorchestrator;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FallbackPolicyProvider {

	private static final String CATEGORY_PROPERTY = "smartdesk.fallback.category";
	private static final String PRIORITY_PROPERTY = "smartdesk.fallback.priority";
	private static final String SUMMARY_PROPERTY = "smartdesk.fallback.summary";
	private static final String SUGGESTED_ANSWER_PROPERTY = "smartdesk.fallback.suggested-answer";
	private static final String SLA_HOURS_PROPERTY = "smartdesk.fallback.sla-hours";
	private static final String SUPPORT_TEAM_PROPERTY = "smartdesk.fallback.support-team";

	private final AtomicReference<FallbackPolicy> policy;
	private final WebClient configServerClient;
	private final String applicationName;

	public FallbackPolicyProvider(
			@Value("${smartdesk.fallback.category}") String category,
			@Value("${smartdesk.fallback.priority}") String priority,
			@Value("${smartdesk.fallback.summary}") String summary,
			@Value("${smartdesk.fallback.suggested-answer}") String suggestedAnswer,
			@Value("${smartdesk.fallback.sla-hours}") int slaHours,
			@Value("${smartdesk.fallback.support-team}") String supportTeam,
			@Value("${smartdesk.config-server.url}") String configServerUrl,
			@Value("${spring.application.name}") String applicationName,
			@Qualifier("webClientBuilder") WebClient.Builder webClientBuilder) {
		this.policy = new AtomicReference<>(new FallbackPolicy(
				category,
				priority,
				summary,
				suggestedAnswer,
				slaHours,
				supportTeam));
		this.configServerClient = webClientBuilder.baseUrl(removeTrailingSlash(configServerUrl)).build();
		this.applicationName = applicationName;
	}

	public FallbackPolicy current() {
		return policy.get();
	}

	public ReloadResult reload() {
		FallbackPolicy oldPolicy = policy.get();
		ConfigEnvironment environment = configServerClient.get()
				.uri("/{application}/default", applicationName)
				.retrieve()
				.bodyToMono(ConfigEnvironment.class)
				.block(Duration.ofSeconds(5));
		FallbackPolicy newPolicy = extractPolicy(environment);
		policy.set(newPolicy);
		return new ReloadResult(oldPolicy, newPolicy, "config-server");
	}

	private FallbackPolicy extractPolicy(ConfigEnvironment environment) {
		if (environment == null || environment.propertySources() == null) {
			throw new IllegalStateException("Config Server did not return property sources.");
		}
		return new FallbackPolicy(
				getRequiredString(environment, CATEGORY_PROPERTY),
				getRequiredString(environment, PRIORITY_PROPERTY),
				getRequiredString(environment, SUMMARY_PROPERTY),
				getRequiredString(environment, SUGGESTED_ANSWER_PROPERTY),
				getRequiredInt(environment, SLA_HOURS_PROPERTY),
				getRequiredString(environment, SUPPORT_TEAM_PROPERTY));
	}

	private String getRequiredString(ConfigEnvironment environment, String propertyName) {
		Object value = getRequiredValue(environment, propertyName);
		return String.valueOf(value);
	}

	private int getRequiredInt(ConfigEnvironment environment, String propertyName) {
		Object value = getRequiredValue(environment, propertyName);
		if (value instanceof Number number) {
			return number.intValue();
		}
		return Integer.parseInt(String.valueOf(value));
	}

	private Object getRequiredValue(ConfigEnvironment environment, String propertyName) {
		for (ConfigPropertySource propertySource : environment.propertySources()) {
			if (propertySource.source() != null && propertySource.source().containsKey(propertyName)) {
				return propertySource.source().get(propertyName);
			}
		}
		throw new IllegalStateException("Property " + propertyName + " was not found in Config Server response.");
	}

	private String removeTrailingSlash(String value) {
		if (value.endsWith("/")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}

	public record FallbackPolicy(
			String category,
			String priority,
			String summary,
			String suggestedAnswer,
			int slaHours,
			String supportTeam) {
	}

	public record ReloadResult(FallbackPolicy oldPolicy, FallbackPolicy newPolicy, String source) {
	}

	record ConfigEnvironment(List<ConfigPropertySource> propertySources) {
	}

	record ConfigPropertySource(String name, Map<String, Object> source) {
	}
}
