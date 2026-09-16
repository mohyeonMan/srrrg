package link.srrrg.utmtemplate.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UtmValueValidatorTest {

	@Test
	void acceptsValueAtLimit() {
		assertThatCode(() -> UtmValueValidator.validate("a".repeat(UtmValueValidator.MAX_VALUE_LENGTH)))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsValueOverLimit() {
		assertThatThrownBy(() -> UtmValueValidator.validate("a".repeat(UtmValueValidator.MAX_VALUE_LENGTH + 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(String.valueOf(UtmValueValidator.MAX_VALUE_LENGTH));
	}

	/**
	 * 값 상한과 활성 필드 수 상한이 합성돼 병합 결과 URL 길이를 묶는다는 전제를 고정한다.
	 * 둘 중 하나가 느슨해지면 여기서 먼저 깨진다.
	 */
	@Test
	void valueLimitBoundsMergedUrlLengthForAsciiValues() {
		int maxFieldNameLength = 50;
		int maxOriginalUrlLength = 2048;
		int worstCase = maxOriginalUrlLength + UtmTemplateService.MAX_ACTIVE_FIELDS
				* (maxFieldNameLength + 1 + UtmValueValidator.MAX_VALUE_LENGTH + 1);

		// 리버스 프록시의 응답 헤더 버퍼(nginx 기본 8KB) 안에 들어가야 Location 헤더가 잘리지 않는다.
		org.assertj.core.api.Assertions.assertThat(worstCase).isLessThan(8192);
	}

	/**
	 * 빈 값은 통과시킨다. 호출부가 빈 값을 저장 대상에서 빼는 것으로 처리하므로
	 * 여기서 막으면 UTM 값을 비워 캠페인 기본값을 상속하는 경로가 끊긴다.
	 */
	@Test
	void acceptsBlankAndNull() {
		assertThatCode(() -> UtmValueValidator.validate("")).doesNotThrowAnyException();
		assertThatCode(() -> UtmValueValidator.validate(null)).doesNotThrowAnyException();
	}
}
