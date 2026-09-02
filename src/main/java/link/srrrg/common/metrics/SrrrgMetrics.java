package link.srrrg.common.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
/**
 * 애플리케이션 메트릭의 이름과 태그를 한곳에서 정의한다.
 *
 * <p>측정 지점마다 {@code MeterRegistry}를 직접 쓰지 않고 이 클래스를 거치게 한 이유는 태그 때문이다.
 * 여러 파드가 같은 메트릭을 보내는데 호출부마다 태그 키나 값 표기가 조금씩 달라지면 시계열이 쪼개져
 * 집계가 어긋난다. 태그로 넘기는 {@code outcome}·{@code result} 값은 코드가 정해둔 고정 문자열만 쓰고,
 * 단축 코드나 사용자 식별자처럼 값의 종류가 무한한 데이터는 태그에 넣지 않는다(카디널리티 폭발).</p>
 */
public class SrrrgMetrics {

	private static final String REDIRECT = "srrrg.redirect";
	private static final String REDIRECT_WRITE = "srrrg.redirect.write";
	private static final String LINK_CREATE = "srrrg.link.create";
	private static final String LINK_CODE_GENERATION = "srrrg.link.code_generation";
	private static final String URL_RISK_CACHE = "srrrg.url.risk.cache";
	private static final String URL_RISK_CHECK = "srrrg.url.risk.check";

	private final MeterRegistry registry;

	public SrrrgMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public Timer.Sample startTimer() {
		return Timer.start(registry);
	}

	public void recordRedirect(Timer.Sample sample, String outcome) {
		sample.stop(Timer.builder(REDIRECT)
				.description("Short URL redirect processing time")
				.tag("outcome", outcome)
				.register(registry));
	}

	/**
	 * 리다이렉트 처리 중 발생하는 DB 쓰기 구간만 따로 측정한다.
	 * 전체 리다이렉트 지연에서 외부 URL 검사 대기와 DB 쓰기 중 어느 쪽이 원인인지 구분하기 위한 것이다.
	 *
	 * @param type 쓰기 종류(현재는 접근 이벤트를 뜻하는 {@code access})
	 * @param outcome 커밋 성공 여부. 예외로 빠져나간 경우도 {@code error}로 남겨야 실패율을 볼 수 있다
	 */
	public void recordRedirectWrite(Timer.Sample sample, String type, String outcome) {
		sample.stop(Timer.builder(REDIRECT_WRITE)
				.description("Access event and counter write transaction time")
				.tag("type", type)
				.tag("outcome", outcome)
				.register(registry));
	}

	public void recordLinkCreate(Timer.Sample sample, String outcome) {
		sample.stop(Timer.builder(LINK_CREATE)
				.description("Short link creation processing time")
				.tag("outcome", outcome)
				.register(registry));
	}

	public void recordLinkCodeGeneration(String outcome) {
		Counter.builder(LINK_CODE_GENERATION)
				.description("Short link code generation results")
				.tag("outcome", outcome)
				.register(registry)
				.increment();
	}

	/**
	 * URL 위험 검사 캐시의 적중 여부를 센다. 외부 검사 호출량과 비용을 추정하는 근거가 된다.
	 */
	public void recordUrlRiskCache(String result) {
		Counter.builder(URL_RISK_CACHE)
				.description("URL risk verification cache lookup results")
				.tag("result", result)
				.register(registry)
				.increment();
	}

	public void recordUrlRiskCheck(Timer.Sample sample, String provider, String outcome) {
		sample.stop(Timer.builder(URL_RISK_CHECK)
				.description("URL risk provider check time")
				.tag("provider", provider)
				.tag("outcome", outcome)
				.register(registry));
	}
}
