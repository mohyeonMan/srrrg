package link.srrrg.common.metrics;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
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
