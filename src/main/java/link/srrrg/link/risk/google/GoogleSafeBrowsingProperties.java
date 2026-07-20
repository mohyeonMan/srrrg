package link.srrrg.link.risk.google;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("srrrg.safe-browsing")
public record GoogleSafeBrowsingProperties(
		String apiKey,
		String endpoint,
		Duration connectTimeout,
		Duration readTimeout
) {
}
