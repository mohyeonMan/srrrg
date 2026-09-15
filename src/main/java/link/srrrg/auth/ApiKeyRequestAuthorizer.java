package link.srrrg.auth;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import link.srrrg.common.PublicApiException;
import link.srrrg.project.ApiKeyPrincipal;
import link.srrrg.project.ApiKeyScope;

/**
 * 공개 API 요청에 필터가 넣은 API key 주체를 꺼내 프로젝트 범위와 scope를 판정한다.
 * 각 컨트롤러가 같은 세 조건을 직접 구현하면 새 경로에서 하나를 빠뜨릴 수 있으므로 이 경계를 공통으로 사용한다.
 *
 * <p>API key 원문 검증과 폐기·만료 확인은 앞단의 {@link ApiKeyAuthenticationFilter}가 담당한다.
 * 여기서는 인증 결과가 존재하는지, 경로가 키의 프로젝트 안인지, 요청 권한을 가졌는지만 확인한다.</p>
 */
@Service
public class ApiKeyRequestAuthorizer {

	/**
	 * 인증된 주체를 반환한다. 프로젝트 id가 경로에 없는 캠페인 통계처럼, 대상 엔티티를 조회한 뒤
	 * 서비스가 프로젝트 일치를 판정해야 하는 경우 이 메서드를 사용한다.
	 */
	public ApiKeyPrincipal requireAuthenticated(HttpServletRequest request) {
		ApiKeyPrincipal principal = (ApiKeyPrincipal) request.getAttribute("srrrg.apiKeyPrincipal");
		if (principal == null) {
			throw new PublicApiException(401, "API_KEY_INVALID", "유효한 API key가 필요합니다.");
		}
		return principal;
	}

	/**
	 * 주체 존재, 프로젝트 일치, scope 보유를 순서대로 확인한다. 프로젝트 검사를 생략해야 하는 호출자는
	 * {@link #requireAuthenticated(HttpServletRequest)}로 주체를 얻고 소유 도메인 서비스에서 범위를 확인해야 한다.
	 */
	public ApiKeyPrincipal require(HttpServletRequest request, Long projectId, ApiKeyScope scope) {
		ApiKeyPrincipal principal = requireAuthenticated(request);
		if (!principal.projectId().equals(projectId)) {
			throw new PublicApiException(403, "PROJECT_ACCESS_DENIED", "다른 프로젝트의 리소스에는 접근할 수 없습니다.");
		}
		if (!principal.scopes().contains(scope)) {
			throw new PublicApiException(403, "SCOPE_REQUIRED", scope.value() + " scope가 필요합니다.");
		}
		return principal;
	}

	/**
	 * 경로에 프로젝트 id가 없을 때 scope만 확인한다. 반환된 주체의 프로젝트와 실제 대상의 프로젝트가
	 * 같은지는 대상 엔티티를 조회하는 application Service가 반드시 확인한다.
	 */
	public ApiKeyPrincipal requireScope(HttpServletRequest request, ApiKeyScope scope) {
		ApiKeyPrincipal principal = requireAuthenticated(request);
		if (!principal.scopes().contains(scope)) {
			throw new PublicApiException(403, "SCOPE_REQUIRED", scope.value() + " scope가 필요합니다.");
		}
		return principal;
	}
}
