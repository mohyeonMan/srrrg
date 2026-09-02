package link.srrrg.link.risk.google;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("srrrg.safe-browsing")
/**
 * Safe Browsing 호출 설정. {@code apiKey}가 비어 있으면 검사를 건너뛰고 판정 불가로 처리하므로,
 * 값을 넣지 않은 환경은 fail-closed 정책에 따라 링크 생성이 막힌다.
 * 두 타임아웃은 리다이렉트 응답 시간에 직접 더해지는 값이다.
 */
public record GoogleSafeBrowsingProperties(
		String apiKey,
		String endpoint,
		Duration connectTimeout,
		Duration readTimeout
) {
}
