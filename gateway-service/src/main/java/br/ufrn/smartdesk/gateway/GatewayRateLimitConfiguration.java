package br.ufrn.smartdesk.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class GatewayRateLimitConfiguration {
}
