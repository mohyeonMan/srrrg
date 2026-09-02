package link.srrrg.project;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * scope를 enum 이름이 아니라 {@code links:read} 같은 API 표기 문자열로 저장한다.
 * 저장 값과 외부 표기를 일치시켜, 응답에 쓰는 문자열과 DB 값이 갈라지지 않게 하려는 것이다.
 * 알 수 없는 값에 예외를 내는 것은 해석하지 못한 scope를 조용히 버려 권한이 사라지는 것을 막기 위해서다.
 */
@Converter
public class ApiKeyScopeConverter implements AttributeConverter<ApiKeyScope, String> {
	@Override
	public String convertToDatabaseColumn(ApiKeyScope scope) {
		return scope == null ? null : scope.value();
	}

	@Override
	public ApiKeyScope convertToEntityAttribute(String value) {
		for (ApiKeyScope scope : ApiKeyScope.values())
			if (scope.value().equals(value))
				return scope;
		throw new IllegalArgumentException("알 수 없는 API key scope입니다.");
	}
}
