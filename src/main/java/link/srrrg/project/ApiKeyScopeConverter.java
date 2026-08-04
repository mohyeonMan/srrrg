package link.srrrg.project;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ApiKeyScopeConverter implements AttributeConverter<ApiKeyScope, String> {
	@Override public String convertToDatabaseColumn(ApiKeyScope scope) { return scope == null ? null : scope.value(); }
	@Override public ApiKeyScope convertToEntityAttribute(String value) {
		for (ApiKeyScope scope : ApiKeyScope.values()) if (scope.value().equals(value)) return scope;
		throw new IllegalArgumentException("알 수 없는 API key scope입니다.");
	}
}
