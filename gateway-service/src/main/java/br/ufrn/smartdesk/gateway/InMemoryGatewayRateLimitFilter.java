package br.ufrn.smartdesk.gateway;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InMemoryGatewayRateLimitFilter extends OncePerRequestFilter {

	private static final String RATE_LIMIT_EXCEEDED_RESPONSE = """
			{"error":"RATE_LIMIT_EXCEEDED","message":"Muitas requisi\u00e7\u00f5es. Tente novamente em alguns segundos."}
			""";

	private final GatewayRateLimitProperties properties;

	private final Counter rejectedCounter;

	private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

	public InMemoryGatewayRateLimitFilter(
			GatewayRateLimitProperties properties,
			MeterRegistry meterRegistry) {
		this.properties = properties;
		this.rejectedCounter = Counter.builder("smartdesk.gateway.rate.limit.rejected")
				.description("Total requests rejected by the SmartDesk Gateway in-memory rate limiter")
				.register(meterRegistry);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (!shouldRateLimit(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		String key = clientKey(request);
		TokenBucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket());

		if (bucket.tryConsume(properties.getCapacity(), refillNanos())) {
			filterChain.doFilter(request, response);
			return;
		}

		rejectedCounter.increment();
		writeTooManyRequests(response);
	}

	private boolean shouldRateLimit(HttpServletRequest request) {
		return properties.isEnabled()
				&& HttpMethod.POST.matches(request.getMethod())
				&& properties.getPath().equals(request.getRequestURI())
				&& properties.getCapacity() > 0
				&& refillNanos() > 0;
	}

	private String clientKey(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private TokenBucket newBucket() {
		return new TokenBucket(properties.getCapacity(), System.nanoTime());
	}

	private long refillNanos() {
		Duration refillPeriod = properties.getRefillPeriod();
		if (refillPeriod == null) {
			return 0;
		}
		return refillPeriod.toNanos();
	}

	private void writeTooManyRequests(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(RATE_LIMIT_EXCEEDED_RESPONSE);
	}

	private static class TokenBucket {

		private int tokens;

		private long lastRefillNanos;

		TokenBucket(int tokens, long lastRefillNanos) {
			this.tokens = tokens;
			this.lastRefillNanos = lastRefillNanos;
		}

		synchronized boolean tryConsume(int capacity, long refillPeriodNanos) {
			refill(capacity, refillPeriodNanos);
			if (tokens <= 0) {
				return false;
			}
			tokens--;
			return true;
		}

		private void refill(int capacity, long refillPeriodNanos) {
			long now = System.nanoTime();
			long elapsed = now - lastRefillNanos;
			long refillPeriods = elapsed / refillPeriodNanos;

			if (refillPeriods <= 0) {
				return;
			}

			long refilledTokens = refillPeriods * capacity;
			tokens = (int) Math.min(capacity, tokens + refilledTokens);
			lastRefillNanos += refillPeriods * refillPeriodNanos;
		}
	}
}
