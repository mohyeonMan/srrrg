package link.srrrg.auth;

/**
 * 쿠키 JWT로 인증된 웹 요청의 주체. 컨트롤러는 {@code @AuthenticationPrincipal}로 이 값을 받는다.
 * 토큰의 subject에서 꺼낸 사용자 식별자만 담고 권한이나 프로필은 담지 않는다.
 * 프로젝트 역할은 요청 시점의 DB 상태로 매번 판단해야 하므로 토큰에 넣지 않는다.
 */
public record SrrrgPrincipal(Long userId) {
}
