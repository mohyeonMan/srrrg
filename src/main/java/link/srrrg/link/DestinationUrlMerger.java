package link.srrrg.link;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 원본 URL의 기존 query와 fragment를 보존하면서 UTM 값을 병합한다.
 * 같은 이름의 기존 query 값은 UTM 값으로 덮어쓰고, 그 외 query는 그대로 유지한다.
 */
public final class DestinationUrlMerger {

	private DestinationUrlMerger() {
	}

	public static String merge(String originalUrl, Map<String, String> utmValues) {
		if (utmValues == null || utmValues.isEmpty()) {
			return originalUrl;
		}

		String fragment = null;
		String withoutFragment = originalUrl;
		int fragmentIndex = originalUrl.indexOf('#');
		if (fragmentIndex >= 0) {
			fragment = originalUrl.substring(fragmentIndex + 1);
			withoutFragment = originalUrl.substring(0, fragmentIndex);
		}

		String base = withoutFragment;
		String rawQuery = null;
		int queryIndex = withoutFragment.indexOf('?');
		if (queryIndex >= 0) {
			rawQuery = withoutFragment.substring(queryIndex + 1);
			base = withoutFragment.substring(0, queryIndex);
		}

		List<String> keptPairs = keepNonUtmPairs(rawQuery, utmValues.keySet());
		List<String> encodedUtmPairs = encodeUtmPairs(utmValues);

		StringBuilder result = new StringBuilder(base);
		List<String> allPairs = new ArrayList<>(keptPairs.size() + encodedUtmPairs.size());
		allPairs.addAll(keptPairs);
		allPairs.addAll(encodedUtmPairs);
		if (!allPairs.isEmpty()) {
			result.append('?').append(String.join("&", allPairs));
		}
		if (fragment != null) {
			result.append('#').append(fragment);
		}
		return result.toString();
	}

	private static List<String> keepNonUtmPairs(String rawQuery, java.util.Set<String> utmFieldNames) {
		List<String> kept = new ArrayList<>();
		if (rawQuery == null || rawQuery.isEmpty()) {
			return kept;
		}
		for (String rawPair : rawQuery.split("&")) {
			if (rawPair.isEmpty()) {
				continue;
			}
			int equalsIndex = rawPair.indexOf('=');
			String rawName = equalsIndex >= 0 ? rawPair.substring(0, equalsIndex) : rawPair;
			String decodedName = decode(rawName);
			if (!utmFieldNames.contains(decodedName)) {
				kept.add(rawPair);
			}
		}
		return kept;
	}

	private static List<String> encodeUtmPairs(Map<String, String> utmValues) {
		// 필드 이름 오름차순으로 결정적인 순서를 만든다. 순서 자체에는 의미가 없다.
		Map<String, String> ordered = new TreeMap<>(utmValues);
		List<String> pairs = new ArrayList<>(ordered.size());
		for (Map.Entry<String, String> entry : ordered.entrySet()) {
			pairs.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
		}
		return pairs;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String decode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException exception) {
			return value;
		}
	}
}
