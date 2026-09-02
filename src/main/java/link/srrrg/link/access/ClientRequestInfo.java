package link.srrrg.link.access;

/**
 * 접근 기록에 남길 요청 정보. 세 값 모두 클라이언트가 보낸 것이라 신뢰할 수 없으며,
 * 통계 분류에만 쓰고 인증이나 권한 판단에는 쓰지 않는다.
 */
public record ClientRequestInfo(
		String ipAddress,
		String referer,
		String userAgent
) {
}
