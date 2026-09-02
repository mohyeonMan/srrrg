package link.srrrg.domain;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 요청의 {@code Host} 헤더와 프로젝트 서브도메인 사이를 오가는 변환을 한곳에 모은다.
 * 링크 주소를 만들 때 쓰는 정방향({@link #hostname}, {@link #origin})과, 들어온 요청이 어느
 * 코드 공간에 속하는지 판정하는 역방향({@link #resolve})이 같은 규칙을 쓰도록 보장하는 것이 이 클래스의 목적이다.
 *
 * <p>이 판정은 신뢰 경계에 있다. 단축 코드는 베이스 도메인과 각 프로젝트 서브도메인마다 독립된 공간이라
 * 같은 코드가 서로 다른 링크를 가리킬 수 있다. {@code Host}는 클라이언트가 보낸 값이므로 그대로 믿지 않고
 * 아래 {@link #parseHostname}에서 형태를 검증한 뒤에만 코드 공간을 결정한다.
 * 이 검증이 느슨해지면 다른 프로젝트의 링크를 조회하게 되는 테넌트 격리 위반이 된다.</p>
 *
 * <p>베이스 호스트는 {@code srrrg.base-url} 설정 하나에서 파생된다. 환경마다 도메인이 다르므로
 * 호스트명을 코드에 적지 않고, 시작 시점에 한 번만 파싱해 보관한다.</p>
 */
@Service
public class ProjectDomainService {
	// 프로젝트가 선점하면 안 되는 서브도메인 목록. 운영 엔드포인트(actuator, api, oauth2, openapi),
	// 메일·인증 관련 레코드(mail, auth, login), 정적 자원(static, cdn), 관례상 서비스가 쓰는 이름(www, app)이
	// 여기 걸린다. 누군가 이 이름으로 프로젝트를 만들면 실제 서비스 경로를 가리거나
	// 신뢰할 만한 주소로 오인하게 만드는 피싱에 쓸 수 있다.
	private static final Set<String> RESERVED_SLUGS = Set.of(
			"actuator", "admin", "api", "app", "auth", "cdn", "cname", "dev", "docs", "help",
			"login", "mail", "manage", "oauth", "oauth2", "openapi", "static", "status", "support", "www");
	private final String baseHostname;
	private final String scheme;
	private final int port;

	/**
	 * 설정된 베이스 URL에서 호스트·스킴·포트를 한 번만 뽑아 보관한다.
	 * 호스트를 얻지 못하면 서브도메인 판정 기준이 없어 모든 리다이렉트가 오작동하므로,
	 * 요청 처리 중에 발견되기를 기다리지 않고 기동 시점에 실패시킨다.
	 */
	public ProjectDomainService(@Value("${srrrg.base-url}") String baseUrl) {
		URI uri = URI.create(baseUrl);
		if (uri.getHost() == null) throw new IllegalArgumentException("srrrg.base-url에 유효한 host가 필요합니다.");
		this.baseHostname = normalizeHostname(uri.getHost());
		this.scheme = uri.getScheme();
		this.port = uri.getPort();
	}

	/**
	 * 프로젝트 생성·수정 시 서브도메인 후보가 예약어인지 확인한다.
	 * 대소문자나 IDN 표기를 정규화하지 않으므로, 호출자가 이미 정규화한 소문자 슬러그를 넘겨야 한다.
	 */
	public boolean isReservedSubdomain(String subdomain) {
		return RESERVED_SLUGS.contains(subdomain);
	}

	public String hostname(String subdomain) {
		return subdomain == null ? baseHostname : subdomain + "." + baseHostname;
	}

	/**
	 * 링크 전체 주소를 만들 때 쓰는 스킴 포함 origin을 돌려준다.
	 * 포트가 음수인 경우는 URL에 포트가 없었다는 뜻이므로 기본 포트로 보고 생략한다.
	 * 로컬 개발처럼 포트가 명시된 환경에서는 그대로 붙여야 만들어진 링크를 그 환경에서 열 수 있다.
	 */
	public String origin(String subdomain) {
		return scheme + "://" + hostname(subdomain) + (port < 0 ? "" : ":" + port);
	}

	/**
	 * 요청 호스트가 어느 코드 공간을 가리키는지 판정한다.
	 *
	 * @param hostHeader 요청의 {@code Host} 헤더 원문. 포트가 붙어 있어도 된다
	 * @return 베이스 도메인이면 서브도메인이 {@code null}인 경로, 프로젝트 서브도메인이면 그 슬러그를 담은 경로.
	 *         우리 도메인이 아니거나 형태를 신뢰할 수 없으면 빈 값이며, 호출자는 이를 404로 처리해야 한다
	 */
	public Optional<HostRoute> resolve(String hostHeader) {
		String hostname = parseHostname(hostHeader);
		if (hostname == null) return Optional.empty();
		// 베이스 도메인은 익명 링크와 서브도메인을 쓰지 않는 프로젝트 링크가 함께 쓰는 공간이다.
		if (baseHostname.equals(hostname)) return Optional.of(new HostRoute(null));
		// 접미사 비교로 우리 도메인 여부를 가른다. 앞에 점을 붙이는 것이 중요하다.
		// 점이 없으면 baseHostname이 "srrrg.link"일 때 공격자가 등록한 "evilsrrrg.link"도 통과한다.
		String suffix = "." + baseHostname;
		String subdomain = hostname.endsWith(suffix) ? hostname.substring(0, hostname.length() - suffix.length()) : "";
		// 점을 포함한 다단 서브도메인은 거부한다. 프로젝트에 발급되는 슬러그는 점을 허용하지 않는
		// 한 단계짜리 이름이라(ProjectService의 서브도메인 정규화 참고) "a.b"라는 프로젝트는 존재할 수 없다.
		// 여기서 통과시키면 있을 수 없는 슬러그로 코드 공간을 고르는 조회가 그대로 진행된다.
		return subdomain.isEmpty() || subdomain.contains(".") ? Optional.empty() : Optional.of(new HostRoute(subdomain));
	}

	/**
	 * {@code Host} 헤더에서 호스트명만 안전하게 뽑아낸다. 신뢰할 수 없으면 {@code null}을 돌려준다.
	 *
	 * <p>문자열을 직접 자르지 않고 {@code URI} 파서에 태우는 이유는, 헤더 값이 클라이언트가 임의로 보낸
	 * 문자열이기 때문이다. {@code user@evil.com}처럼 userinfo가 섞이거나 경로·쿼리·프래그먼트가 붙은 값은
	 * 사람 눈에는 우리 도메인처럼 보이면서 실제 파싱 결과는 다를 수 있다. 이런 값을 통과시키면
	 * 잘못된 코드 공간을 고르거나, 뽑아낸 호스트를 그대로 쓰는 다른 코드에서 위조된 주소가 만들어진다.
	 * 그래서 순수한 호스트(+포트) 형태가 아니면 전부 거부한다.</p>
	 */
	private String parseHostname(String hostHeader) {
		if (hostHeader == null || hostHeader.isBlank()) return null;
		try {
			// URI 파서는 스킴이 필요하므로 임시로 붙인다. 실제 요청 스킴과는 무관하며 호스트만 꺼내 쓴다.
			URI uri = URI.create("http://" + hostHeader.trim());
			if (uri.getHost() == null || uri.getUserInfo() != null || !uri.getPath().isEmpty()
					|| uri.getQuery() != null || uri.getFragment() != null) return null;
			return normalizeHostname(uri.getHost());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/**
	 * 같은 호스트를 가리키는 여러 표기를 하나로 모은다. 정방향 생성과 역방향 판정이 같은 함수를 쓰므로
	 * 두 경로의 표기가 어긋날 일이 없다.
	 *
	 * <p>끝의 점(FQDN 표기)을 떼고, IDN 한글·유니코드 도메인을 punycode로 바꾼 뒤 소문자로 맞춘다.
	 * DNS는 대소문자를 구분하지 않지만 문자열 비교는 구분하므로, 이 단계를 빠뜨리면
	 * {@code Srrrg.link}나 {@code srrrg.link.}이 우리 도메인이 아닌 것으로 판정된다.</p>
	 */
	private String normalizeHostname(String hostname) {
		String normalized = hostname.endsWith(".") ? hostname.substring(0, hostname.length() - 1) : hostname;
		return IDN.toASCII(normalized).toLowerCase(Locale.ROOT);
	}

	/**
	 * 판정된 코드 공간. {@code subdomain}이 {@code null}이면 베이스 도메인 공간을 뜻하고,
	 * 링크 조회 쿼리도 이에 따라 {@code subdomain IS NULL}과 값 비교로 갈린다.
	 */
	public record HostRoute(String subdomain) {
		public boolean isBaseDomain() { return subdomain == null; }
	}
}
