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
/**
 * 원본 URL의 기존 query와 fragment를 보존하면서 UTM 값을 병합한다.
 * 같은 이름의 기존 query 값은 UTM 값으로 덮어쓰고, 그 외 query는 그대로 유지한다.
 *
 * <p>URI 파서를 쓰지 않고 문자열을 직접 자르는 이유는 원본을 최대한 그대로 두기 위해서다.
 * 파싱 후 재조립하면 인코딩 표기나 파라미터 순서가 바뀌어, 서명이나 정확한 일치를 요구하는
 * 목적지에서 링크가 깨진다. 그래서 UTM과 겹치지 않는 기존 쌍은 원문 그대로 옮긴다.</p>
 *
 * <p>fragment를 떼었다 마지막에 다시 붙이는 것은 순서 때문이다. 물음표보다 뒤에 있는 우물 정 기호는
 * query의 일부가 아니라 fragment의 시작이므로, 먼저 분리하지 않으면 query에 섞여 들어간다.</p>
 */
public final class DestinationUrlMerger {

	private DestinationUrlMerger() {
	}

	/**
	 * UTM 값을 합친 최종 목적지를 만든다. 이 결과가 리다이렉트에서 실제로 사용되는 주소다.
	 *
	 * @param originalUrl 링크의 목적지. 이미 query나 fragment가 붙어 있어도 된다
	 * @param utmValues 필드 이름과 값. 비어 있으면 원본을 그대로 돌려준다
	 * @return 병합된 URL. 같은 입력이면 항상 같은 문자열이 나온다
	 */
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

	/**
	 * UTM과 이름이 겹치지 않는 기존 query 쌍만 남긴다. 겹치는 쌍을 버려야 같은 파라미터가 두 번 실리지 않는다.
	 *
	 * <p>이름은 디코딩해서 비교하되 남길 때는 원문 그대로 옮긴다. 인코딩 표기만 다른 같은 이름을 놓치지 않으면서,
	 * 유지되는 파라미터의 표기는 건드리지 않기 위해서다.</p>
	 */
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

	/**
	 * 디코딩에 실패하면 원문을 그대로 쓴다. 깨진 인코딩 하나 때문에 병합 전체가 실패하면
	 * 리다이렉트가 막히므로, 이름 비교가 어긋날 위험을 감수하고 진행한다.
	 */
	private static String decode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException exception) {
			return value;
		}
	}
}
