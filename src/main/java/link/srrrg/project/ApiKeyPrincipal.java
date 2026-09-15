package link.srrrg.project;

import java.util.Set;

/**
 * 인증된 프로젝트 API key의 주체다. 사용자 계정이 아니라 발급 프로젝트에 묶이며,
 * {@code /api/v1/**} 요청의 프로젝트 범위와 scope를 판정할 때 사용한다.
 *
 * <p>필터가 인증에 성공한 뒤 request 속성과 Spring Security context에 같은 인스턴스를 넣는다.
 * 원본 API key는 인증 단계 이후에 전달하지 않아 로그나 오류 응답으로 새는 경로를 만들지 않는다.</p>
 */
public record ApiKeyPrincipal(Long keyId, Long projectId, Set<ApiKeyScope> scopes) {
}
