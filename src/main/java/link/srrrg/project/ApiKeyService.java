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

/**
 * 프로젝트 API 키의 발급, 폐기, 인증을 담당한다. {@code /api/v1/**} 표면의 모든 요청이
 * {@link #authenticate}를 거치므로 이 클래스가 그 표면의 신뢰 근거다.
 *
 * <p>키 원문은 발급 응답에만 나가고 DB에는 해시만 남는다. 조회 목록에는 앞부분 식별용 문자열만 보여
 * 사용자가 어느 키인지 알아볼 수 있게 한다.</p>
 *
 * <p>발급·조회·폐기는 OWNER만 할 수 있다. 키는 사실상 프로젝트 전체에 대한 접근 수단이라
 * 링크를 만들 수 있는 EDITOR보다 좁은 권한을 요구한다.</p>
 */
@Service
public class ApiKeyService {
	private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private final ProjectApiKeyRepository keys;
	private final ProjectAccessService projectAccess;
	private final ProjectRepository projects;
	private final UserRepository users;
	private final SecureRandomStringGenerator random;

	public ApiKeyService(ProjectApiKeyRepository keys, ProjectAccessService projectAccess, ProjectRepository projects,
			UserRepository users, SecureRandomStringGenerator random) {
		this.keys = keys;
		this.projectAccess = projectAccess;
		this.projects = projects;
		this.users = users;
		this.random = random;
	}

	@Transactional(readOnly = true)
	public List<ProjectApiKey> list(Long userId, Long projectId) {
		projectAccess.requireRole(userId, projectId, ProjectRole.OWNER);
		return keys.findByProjectIdOrderByCreatedAtDesc(projectId);
	}

	/**
	 * 새 API 키를 발급한다. scope를 최소 하나 요구하는 것은 아무것도 못 하는 키가 만들어지는 것을 막기 위해서다.
	 *
	 * <p>키는 접두사와 난수 두 부분으로 나뉜다. 앞의 8자는 목록에서 키를 식별하기 위해 평문으로 저장하고,
	 * 뒤의 43자만이 실제 비밀이다. 접두사는 해시 대상에 포함되므로 그것만으로는 인증할 수 없다.</p>
	 *
	 * @return 저장된 키 엔티티와 원문. 원문은 이 반환값에만 존재한다
	 */
	@Transactional
	public CreatedKey create(Long userId, Long projectId, String name, Set<ApiKeyScope> scopes, Instant expiresAt) {
		projectAccess.requireRole(userId, projectId, ProjectRole.OWNER);
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

	/**
	 * 키를 즉시 폐기한다. 행을 지우지 않고 시각만 남기는 것은 어떤 키가 언제까지 쓰였는지 기록을 남기기 위해서다.
	 * 키 id만으로 찾은 뒤 프로젝트 소속을 다시 확인하는 것이 중요하다. 확인하지 않으면 남의 프로젝트 키를 폐기할 수 있다.
	 */
	@Transactional
	public void revoke(Long userId, Long projectId, Long keyId) {
		projectAccess.requireRole(userId, projectId, ProjectRole.OWNER);
		ProjectApiKey key = keys.findById(keyId).orElseThrow(() -> new IllegalArgumentException("API key를 찾을 수 없습니다."));
		if (!key.getProject().getId().equals(projectId))
			throw new IllegalArgumentException("API key를 찾을 수 없습니다.");
		key.revoke();
	}

	/**
	 * 제시된 키 원문을 검증하고 주체를 만든다. 인증 필터가 매 v1 요청마다 호출한다.
	 *
	 * <p>없는 키, 폐기된 키, 만료된 키를 모두 같은 예외로 합친다. 구분해 주면 유효한 키를 탐색하는 데 단서가 된다.</p>
	 *
	 * <p>읽기 트랜잭션이 아닌 것은 마지막 사용 시각을 기록하기 때문이다. 인증 경로마다 쓰기가 한 번 생기지만,
	 * 쓰이지 않는 키를 찾아 정리하려면 이 기록이 필요하다.</p>
	 *
	 * @return 키 id, 소속 프로젝트, 허용 scope. 실제 권한 판정은 이 값을 받은 컨트롤러가 수행한다
	 */
	@Transactional
	public ApiKeyPrincipal authenticate(String raw) {
		ProjectApiKey key = keys.findActiveByKeyHash(hash(raw)).orElseThrow(ApiKeyUnauthorizedException::new);
		if (!key.isUsableAt(Instant.now()))
			throw new ApiKeyUnauthorizedException();
		key.recordUse();
		return new ApiKeyPrincipal(key.getId(), key.getProject().getId(), Set.copyOf(key.getScopes()));
	}

	private Project project(Long id) {
		return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
	}

	private User user(Long id) {
		return users.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
	}

	/**
	 * 키 원문을 저장·조회용 해시로 바꾼다. 고엔트로피 난수라 솔트 없는 SHA-256으로 충분하며,
	 * 매 요청 이 값으로 행을 찾아야 하므로 같은 입력이 항상 같은 결과를 내야 한다.
	 */
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

	/**
	 * API key 요청의 주체. 웹의 {@code SrrrgPrincipal}과 달리 사용자가 아니라 프로젝트에 묶여 있다.
	 * 컨트롤러는 이 값의 {@code projectId}가 요청 경로의 프로젝트와 같은지, 필요한 scope를 가졌는지 확인해야 한다.
	 */
	public record ApiKeyPrincipal(Long keyId, Long projectId, Set<ApiKeyScope> scopes) {
	}

	public static class ApiKeyUnauthorizedException extends RuntimeException {
	}
}
