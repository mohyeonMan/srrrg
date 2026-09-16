package link.srrrg.auth.login.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.identity.connection.model.OAuthIdentity;
import link.srrrg.identity.connection.service.OAuthIdentityService;
import link.srrrg.identity.connection.service.OAuthIdentityService.LoginResolution;
import link.srrrg.project.service.ProjectService;
import lombok.RequiredArgsConstructor;

/**
 * 공급자 신원을 내부 계정으로 확정하고 로그인 직후 필요한 개인 프로젝트를 준비한다.
 * 계정 식별은 identity 기능이, 프로젝트 생성은 project 기능이 소유하며 이 서비스는 로그인 순서만 조율한다.
 */
@Service
@RequiredArgsConstructor
public class LoginCompletionService {

	private final OAuthIdentityService identityService;
	private final ProjectService projectService;

	/**
	 * 연결 확인이 필요하지 않은 로그인에만 개인 프로젝트를 보장한다. 확인 전 생성하면 완료되지 않은
	 * 로그인 시도마다 빈 프로젝트가 남기 때문에 계정 확정 뒤에만 실행한다.
	 */
	@Transactional
	public LoginResolution complete(OAuthIdentity identity) {
		LoginResolution resolution = identityService.resolve(identity);
		if (!resolution.requiresLink()) {
			projectService.ensurePersonalProject(resolution.user().getId());
		}
		return resolution;
	}
}
