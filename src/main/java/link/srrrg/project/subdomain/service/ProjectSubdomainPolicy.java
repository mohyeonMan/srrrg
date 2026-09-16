package link.srrrg.project.subdomain.service;

import java.util.Set;

import org.springframework.stereotype.Component;

/** 프로젝트가 선점할 수 없는 플랫폼·운영용 서브도메인 이름을 판정한다. */
@Component
public class ProjectSubdomainPolicy {
	private static final Set<String> RESERVED_SLUGS = Set.of(
			"actuator", "admin", "api", "app", "auth", "cdn", "cname", "dev", "docs", "help",
			"login", "mail", "manage", "oauth", "oauth2", "openapi", "static", "status", "support", "www");

	public boolean isReserved(String subdomain) {
		return RESERVED_SLUGS.contains(subdomain);
	}
}
