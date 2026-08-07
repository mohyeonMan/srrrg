package link.srrrg.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DestinationUrlMergerTest {

	@Test
	void returnsOriginalUrlUnchangedWhenNoUtmValues() {
		assertThat(DestinationUrlMerger.merge("https://example.com/path?q=1", Map.of()))
				.isEqualTo("https://example.com/path?q=1");
	}

	@Test
	void appendsUtmValuesToUrlWithoutQuery() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("utm_source", "newsletter");
		String merged = DestinationUrlMerger.merge("https://example.com/path", values);
		assertThat(merged).isEqualTo("https://example.com/path?utm_source=newsletter");
	}

	@Test
	void preservesExistingNonUtmQueryParameters() {
		Map<String, String> values = Map.of("utm_source", "newsletter");
		String merged = DestinationUrlMerger.merge("https://example.com/path?q=1&lang=ko", values);
		assertThat(merged).contains("q=1").contains("lang=ko").contains("utm_source=newsletter");
	}

	@Test
	void overwritesExistingQueryValueWithSameUtmName() {
		Map<String, String> values = Map.of("utm_source", "newsletter");
		String merged = DestinationUrlMerger.merge("https://example.com/path?utm_source=old&q=1", values);
		assertThat(merged).contains("utm_source=newsletter").contains("q=1").doesNotContain("utm_source=old");
	}

	@Test
	void preservesFragmentAfterQuery() {
		Map<String, String> values = Map.of("utm_source", "newsletter");
		String merged = DestinationUrlMerger.merge("https://example.com/path?q=1#section", values);
		assertThat(merged).endsWith("#section");
		assertThat(merged.indexOf('#')).isGreaterThan(merged.indexOf('?'));
	}

	@Test
	void urlEncodesUtmNamesAndValues() {
		Map<String, String> values = Map.of("utm_source", "a b&c");
		String merged = DestinationUrlMerger.merge("https://example.com/path", values);
		assertThat(merged).doesNotContain("a b&c");
		assertThat(merged).contains("utm_source=a+b%26c");
	}

	@Test
	void ordersMultipleUtmFieldsAlphabeticallyForDeterminism() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("utm_source", "s");
		values.put("utm_medium", "m");
		String merged = DestinationUrlMerger.merge("https://example.com/path", values);
		assertThat(merged.indexOf("utm_medium")).isLessThan(merged.indexOf("utm_source"));
	}
}
