package br.ufrn.smartdesk.gateway;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartdesk.gateway.rate-limit")
public class GatewayRateLimitProperties {

	private boolean enabled = true;

	private int capacity = 10;

	private Duration refillPeriod = Duration.ofSeconds(10);

	private String path = "/api/tickets/analyze";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getCapacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public Duration getRefillPeriod() {
		return refillPeriod;
	}

	public void setRefillPeriod(Duration refillPeriod) {
		this.refillPeriod = refillPeriod;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}
}
