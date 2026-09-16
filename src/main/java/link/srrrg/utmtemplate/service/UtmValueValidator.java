package link.srrrg.utmtemplate.service;

/**
 * UTM 필드에 넣을 값이 저장하고 URL에 실어도 되는 길이인지 검사한다.
 * 링크에 직접 넣는 값, 캠페인 기본값, CSV 업로드 값이 모두 이 검사를 거친다.
 *
 * <p>값 하나의 상한을 두는 이유는 최종 목적지 URL 길이를 쓰기 시점에 묶기 위해서다. 리다이렉트가
 * 실제로 사용하는 주소는 목적지에 UTM 값을 합친 결과인데, 그 결과는 저장되지 않고 매 요청 계산된다.
 * 게다가 캠페인 기본값은 링크를 만든 뒤에도 바뀌므로, 링크 생성 시점에 병합 결과 길이를 재 봐야
 * 나중에 기본값 하나만 추가되면 그대로 무효가 된다. 값별 상한은 다르다. 활성 필드 수가
 * {@link UtmTemplateService#MAX_ACTIVE_FIELDS}개로 이미 묶여 있으므로 값별 상한이 전체 길이 상한으로
 * 합성되고, 나중에 어떤 값이 추가·변경돼도 그 상한이 유지된다.</p>
 *
 * <p>그래서 목적지 URL 자체는 {@code UrlValidator}로 원본만 검사하고, 병합 결과를 다시 검사하지 않는다.
 * 병합은 스킴·호스트·경로를 건드리지 않고 query만 재조립하므로, 원본이 통과하면 병합 결과도
 * 프로토콜·호스트·사설망 검사를 똑같이 통과한다. 길이만 달라지는데 그 길이를 여기서 묶는다.</p>
 *
 * <p>상태가 없어 {@code DestinationUrlMerger}와 같은 정적 유틸로 둔다. 빈으로 올리면 호출부 세 곳에
 * 생성자 주입만 늘고 얻는 것이 없다.</p>
 */
public final class UtmValueValidator {

	/**
	 * 값 하나의 최대 길이. GA4의 campaign source·medium·name 상한과 같은 값이라,
	 * 이 서비스가 만든 링크를 GA로 받는 쪽에서 잘리지 않는다.
	 */
	public static final int MAX_VALUE_LENGTH = 100;

	private UtmValueValidator() {
	}

	/**
	 * @param value 검사할 UTM 값
	 * @throws IllegalArgumentException 상한을 넘은 경우. 메시지는 사용자에게 그대로 노출된다
	 */
	// ponytail: 글자 수만 센다. 한글처럼 UTF-8 3바이트인 글자는 URL 인코딩에서 9자로 부풀어,
	// 최악의 경우(10개 필드 전부 100자 한글) 병합 결과가 약 11KB까지 커진다. 리버스 프록시의
	// 응답 헤더 버퍼(nginx 기본 8k)를 넘기면 그 링크는 리다이렉트에서 죽는다. 실사용 UTM 값은
	// 영문 수십 자라 현실에서 닿지 않고, 닿더라도 자기 프로젝트 링크만 망가지는 자해라 여기서는
	// 글자 수로 둔다. 실제로 문제가 되면 상한을 62자로 낮춰(2,048 + 10 × (50 + 1 + 9 × 62 + 1) ≤ 8KB)
	// 정적 보장으로 올리거나, 인코딩 후 길이로 재는 방법이 있다.
	public static void validate(String value) {
		if (value != null && value.length() > MAX_VALUE_LENGTH) {
			throw new IllegalArgumentException("UTM 값은 " + MAX_VALUE_LENGTH + "자 이하여야 합니다.");
		}
	}
}
