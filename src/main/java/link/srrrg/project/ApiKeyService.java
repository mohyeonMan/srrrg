package link.srrrg.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.common.util.SecureRandomStringGenerator;
import link.srrrg.identity.User;
import link.srrrg.identity.UserRepository;

@Service
public class ApiKeyService {
	private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private final ProjectApiKeyRepository keys;
	private final ProjectMemberRepository members;
	private final ProjectRepository projects;
	private final UserRepository users;
	private final SecureRandomStringGenerator random;

	public ApiKeyService(ProjectApiKeyRepository keys, ProjectMemberRepository members, ProjectRepository projects,
			UserRepository users, SecureRandomStringGenerator random) {
		this.keys = keys;
		this.members = members;
		this.projects = projects;
		this.users = users;
		this.random = random;
	}

	@Transactional(readOnly = true)
	public List<ProjectApiKey> list(Long userId, Long projectId) {
		requireOwner(userId, projectId);
		return keys.findByProjectIdOrderByCreatedAtDesc(projectId);
	}

	@Transactional
	public CreatedKey create(Long userId, Long projectId, String name, Set<ApiKeyScope> scopes, Instant expiresAt) {
		requireOwner(userId, projectId);
		if (name == null || name.isBlank() || name.trim().length() > 100)
			throw new IllegalArgumentException("API key 이름은 1~100자로 입력하세요.");
		if (scopes == null || scopes.isEmpty())
			throw new IllegalArgumentException("API key scope를 하나 이상 선택하세요.");
		if (expiresAt != null && !expiresAt.isAfter(Instant.now()))
			throw new IllegalArgumentException("만료 시각은 미래여야 합니다.");
		String prefix = random.generate(CHARS, 8);
		String raw = "srrrg_pk_" + prefix + "_" + random.generate(CHARS, 43);
		ProjectApiKey key = ProjectApiKey.create(project(projectId), name.trim(), prefix, hash(raw), user(userId),
				scopes, expiresAt);
		keys.save(key);
		return new CreatedKey(key, raw);
	}

	@Transactional
	public void revoke(Long userId, Long projectId, Long keyId) {
		requireOwner(userId, projectId);
		ProjectApiKey key = keys.findById(keyId).orElseThrow(() -> new IllegalArgumentException("API key를 찾을 수 없습니다."));
		if (!key.getProject().getId().equals(projectId))
			throw new IllegalArgumentException("API key를 찾을 수 없습니다.");
		key.revoke();
	}

	@Transactional
	public ApiKeyPrincipal authenticate(String raw) {
		ProjectApiKey key = keys.findActiveByKeyHash(hash(raw)).orElseThrow(ApiKeyUnauthorizedException::new);
		if (!key.isUsableAt(Instant.now()))
			throw new ApiKeyUnauthorizedException();
		key.recordUse();
		return new ApiKeyPrincipal(key.getId(), key.getProject().getId(), Set.copyOf(key.getScopes()));
	}

	// findActiveByProjectAndUser가 프로젝트를 조인하므로 삭제된 프로젝트에서는 키를 발급·조회·폐기할 수 없다.
	private void requireOwner(Long userId, Long projectId) {
		ProjectMember member = members.findActiveByProjectAndUser(projectId, userId)
				.orElseThrow(() -> new SecurityException("프로젝트 접근 권한이 없습니다."));
		if (member.getRole() != ProjectRole.OWNER)
			throw new SecurityException("프로젝트 접근 권한이 없습니다.");
	}

	private Project project(Long id) {
		return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
	}

	private User user(Long id) {
		return users.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	static String hash(String value) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public record CreatedKey(ProjectApiKey key, String rawKey) {
	}

	public record ApiKeyPrincipal(Long keyId, Long projectId, Set<ApiKeyScope> scopes) {
	}

	public static class ApiKeyUnauthorizedException extends RuntimeException {
	}
}
