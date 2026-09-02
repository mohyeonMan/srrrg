package link.srrrg.link;

/**
 * 위험 검사를 수행하지 못해 판정이 없는 상태. 안전하다는 뜻이 아니므로 통과시키지 않는(fail-closed) 정책의 결과다.
 * 일시적일 수 있어 503으로 변환되며, 클라이언트는 잠시 뒤 다시 시도할 수 있다.
 */
public class UrlRiskCheckFailedException extends RuntimeException {
	public UrlRiskCheckFailedException() {
		super("URL 안전 검사를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
	}
}
