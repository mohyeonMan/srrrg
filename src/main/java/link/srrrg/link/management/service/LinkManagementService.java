package link.srrrg.link.management.service;

import link.srrrg.link.creation.service.LinkCodeGenerator;
import link.srrrg.link.repository.LinkRepository;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Timer;
import link.srrrg.common.metrics.SrrrgMetrics;
import link.srrrg.link.address.service.LinkAddressService;
import link.srrrg.link.creation.service.LinkCreationService;
import link.srrrg.link.model.Link;
import link.srrrg.link.model.LinkNotFoundException;
import link.srrrg.link.anonymous.service.SecretKeyManager;
import link.srrrg.link.anonymous.service.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.risk.model.UnsafeUrlException;
import link.srrrg.link.risk.model.UrlRiskCheckFailedException;
import link.srrrg.link.creation.service.UrlValidator;
import link.srrrg.link.management.dto.CreateLinkRequest;
import link.srrrg.link.management.dto.CreateLinkResponse;
import link.srrrg.link.management.dto.DeleteLinkResponse;
import link.srrrg.link.management.dto.LinkManagementResponse;
import link.srrrg.link.management.dto.UpdateLinkRequest;
import link.srrrg.link.risk.model.RiskVerdict;
import link.srrrg.link.risk.service.UrlRiskVerificationService;
import lombok.extern.slf4j.Slf4j;

/**
 * 익명 링크의 생성·조회·수정·삭제와 프로젝트 링크의 관리 응답·수정을 담당한다.
 * 인증된 프로젝트·캠페인 링크 생성은 {@link LinkCreationService}가 맡는다.
 *
 * <p>가장 큰 차이는 URL 위험 검사다. 익명 링크는 누구나 만들 수 있어 신뢰할 수 없는 입력으로 보고
 * 저장 전에 검사하지만, 프로젝트 링크는 인증된 멤버나 API key 소유자가 만든다고 보아 생략한다.
 * 아래 각 생성 메서드에 이 선택을 되돌릴 지점을 적어 두었다. 형식과 SSRF 검증은 종류와 무관하게 항상 한다.</p>
 *
 * <p>인가는 이 클래스의 책임이 아니다. 익명 경로만 secret key를 직접 대조하고, 프로젝트 경로는
 * 호출자가 이미 확인한 {@link Link}를 넘겨받는다. 그래서 프로젝트용 메서드에 링크 조회가 없다.</p>
 *
 * <p>단축 코드 유일성은 미리 확인하지 않고 저장 실패로 감지한다. 여러 파드가 동시에 만들기 때문에
 * 사전 조회는 보장이 되지 않으며, DB 유일 제약만이 최종 판정이다.</p>
 */
@Service
@Slf4j
public class LinkManagementService {

	// 코드 충돌 시 재시도 횟수. 62^6 공간에서 다섯 번 연속 충돌은 사실상 코드 공간이 포화됐거나
	// 다른 제약을 충돌로 오인하고 있다는 뜻이므로, 무한히 도는 대신 실패로 끝낸다.
	private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

	private final LinkRepository linkRepository;
	private final LinkCodeGenerator linkCodeGenerator;
	private final SecretKeyManager secretKeyManager;
	private final UrlValidator urlValidator;
	private final UrlRiskVerificationService riskVerificationService;
	private final SrrrgMetrics metrics;
	private final LinkAddressService addresses;
	private final String baseUrl;

	public LinkManagementService(LinkRepository linkRepository, LinkCodeGenerator linkCodeGenerator,
			SecretKeyManager secretKeyManager, UrlValidator urlValidator,
			UrlRiskVerificationService riskVerificationService,
			SrrrgMetrics metrics, LinkAddressService addresses,
			@Value("${srrrg.base-url}") String baseUrl) {
		this.linkRepository = linkRepository;
		this.linkCodeGenerator = linkCodeGenerator;
		this.secretKeyManager = secretKeyManager;
		this.urlValidator = urlValidator;
		this.riskVerificationService = riskVerificationService;
		this.metrics = metrics;
		this.addresses = addresses;
		this.baseUrl = removeTrailingSlash(baseUrl);
	}

	/**
	 * 익명 링크를 만든다. 비회원이 호출하는 경로라 검증을 모두 통과해야 저장한다.
	 *
	 * <p>secret key 원문은 이 응답에만 담긴다. 이후에는 해시만 남아 다시 알려줄 방법이 없으므로,
	 * 응답을 잃어버리면 그 링크는 관리할 수 없다.</p>
	 *
	 * @return 단축 코드, 완성된 단축 URL, 관리용 secret key 원문, 만료 시각
	 * @throws UnsafeUrlException 위협으로 판정된 URL
	 * @throws UrlRiskCheckFailedException 위험 검사를 수행하지 못한 경우. 재시도하면 성공할 수 있다
	 * @throws IllegalArgumentException URL 형식이나 만료 시각이 올바르지 않은 경우
	 */
	public CreateLinkResponse create(CreateLinkRequest request) {
		Timer.Sample sample = metrics.startTimer();
		String outcome = "error";
		try {
			log.debug("Link creation started: expiresAtPresent={}", request.expiresAt() != null);

			urlValidator.validate(request.originalUrl());
			validateExpiration(request.expiresAt());
			// 신규 링크는 위협 미탐지 결과가 있어야만 저장함.
			requireNoKnownThreat(request.originalUrl());

			GeneratedSecretKey secretKey = secretKeyManager.generate();
			Link savedLink = saveWithUniqueCode(request.originalUrl(), secretKey.hash(), request.expiresAt());
			log.info("Link created: code={}, expiresAtPresent={}", savedLink.getCode(), savedLink.getExpiresAt() != null);
			outcome = "created";
			return new CreateLinkResponse(savedLink.getCode(), baseUrl + "/" + savedLink.getCode(),
					secretKey.value(), savedLink.getExpiresAt());
		} catch (UnsafeUrlException exception) {
			outcome = "threat";
			throw exception;
		} catch (UrlRiskCheckFailedException exception) {
			outcome = "check_failed";
			throw exception;
		} catch (IllegalArgumentException exception) {
			outcome = "invalid";
			throw exception;
		} finally {
			metrics.recordLinkCreate(sample, outcome);
		}
	}

	@Transactional(readOnly = true)
	public LinkManagementResponse getManagedLink(String code, String secretKey) {
		return toManagementResponse(requireManagedLink(code, secretKey), true, null);
	}

	/**
	 * secret key로 인증하고 익명 링크를 수정한다.
	 *
	 * <p>트랜잭션을 열지 않는 것이 의도다. 외부 위험 검사가 끼어 있어 트랜잭션 안에 넣으면
	 * 그 응답을 기다리는 동안 DB 커넥션을 점유한다. 대신 조회와 저장이 별개 트랜잭션이 되므로,
	 * 그 사이 다른 요청이 같은 링크를 바꿨다면 이 저장이 덮어쓴다.</p>
	 *
	 * @throws LinkNotFoundException 링크가 없거나 secret key가 맞지 않는 경우. 둘을 구분하지 않는다
	 */
	public LinkManagementResponse updateManagedLink(String code, String secretKey, UpdateLinkRequest request) {
		// 외부 검사 시간 동안 DB 트랜잭션을 유지하지 않고 검사가 끝난 뒤 저장함.
		log.debug("Managed link update started: code={}", code);
		Link link = requireManagedLink(code, secretKey);
		validateUpdateRequest(request);
		boolean urlChanged = request.isOriginalUrlPresent()
		// 같은 URL을 다시 보낸 경우까지 외부 검사를 부르지 않도록 실제 변경 여부를 먼저 판단한다.
				&& !link.getOriginalUrl().equals(request.getOriginalUrl());
		if (request.isOriginalUrlPresent()) {
			urlValidator.validate(request.getOriginalUrl());
		}
		if (request.isExpiresAtPresent()) {
			validateExpiration(request.getExpiresAt());
		}
		if (urlChanged) {
			// 원본 URL이 실제로 바뀐 경우에만 새 URL을 검사함.
			requireNoKnownThreat(request.getOriginalUrl());
			link.updateOriginalUrl(request.getOriginalUrl());
		}
		if (request.isExpiresAtPresent()) {
			link.updateExpiresAt(request.getExpiresAt());
		}
		Link savedLink = linkRepository.save(link);
		log.info("Managed link updated: code={}, originalUrlChanged={}, expiresAtChanged={}",
				code, urlChanged, request.isExpiresAtPresent());
		return toManagementResponse(savedLink, true, null);
	}

	public LinkManagementResponse projectManagementResponse(Link link, boolean editable) {
		return toManagementResponse(link, editable, projectShortUrl(link));
	}

	/**
	 * 프로젝트·캠페인 링크를 수정한다. 인가는 호출자가 이미 끝냈다고 보고 여기서 다시 확인하지 않으므로,
	 * 확인 없이 조회한 {@link Link}를 넘기면 남의 링크도 수정된다.
	 *
	 * <p>캠페인 링크는 목적지를 비울 수 있다. 비우면 캠페인 기본 목적지를 상속하기 때문이며,
	 * 상속할 대상이 없는 단일 링크에서는 거부한다.</p>
	 */
	public LinkManagementResponse updateProjectLink(Link link, UpdateLinkRequest request) {
		validateUpdateRequest(request);
		boolean urlChanged = request.isOriginalUrlPresent() && !Objects.equals(link.getOriginalUrl(), request.getOriginalUrl());
		if (request.isOriginalUrlPresent()) {
			if (request.getOriginalUrl() == null || request.getOriginalUrl().isBlank()) {
				if (link.getCampaign() == null) throw new IllegalArgumentException("프로젝트 단일 링크에는 목적지 URL이 필요합니다.");
			} else {
				urlValidator.validate(request.getOriginalUrl());
			}
			// 로그인 프로젝트 멤버를 신뢰하므로 URL 위험 검사는 의도적으로 생략한다.
			// 신뢰 정책이 바뀌면 requireNoKnownThreat를 이 지점에 복구한다.
			if (urlChanged) link.updateOriginalUrl(request.getOriginalUrl());
		}
		if (request.isExpiresAtPresent()) {
			validateExpiration(request.getExpiresAt());
			link.updateExpiresAt(request.getExpiresAt());
		}
		return toManagementResponse(linkRepository.save(link), true, projectShortUrl(link));
	}

	/**
	 * secret key로 인증하고 익명 링크를 삭제한다. soft delete라 행은 남지만 이후 모든 조회에서 빠지고,
	 * 그 코드로 오는 요청은 404가 된다. 이미 쌓인 통계 이벤트가 이 링크를 참조하므로 물리 삭제하지 않는다.
	 */
	@Transactional
	public DeleteLinkResponse deleteManagedLink(String code, String secretKey) {
		// @SoftDelete가 걸려 있어 delete()는 deleted_at을 찍는 UPDATE로 번역된다.
		linkRepository.delete(requireManagedLink(code, secretKey));
		log.info("Managed link deleted: code={}", code);
		return new DeleteLinkResponse(true);
	}

	/**
	 * 위험 검사를 통과하지 못하면 저장을 막는다. UNKNOWN을 통과시키지 않는 것이 핵심이다.
	 * 검사에 실패했을 뿐 안전하다는 뜻이 아니므로, 검사 서비스가 복구될 때까지 fail-closed로 거부한다.
	 *
	 * @throws UnsafeUrlException 위협 판정. 재시도해도 같은 결과인 영구 거부다
	 * @throws UrlRiskCheckFailedException 판정 불가. 일시적일 수 있어 다른 상태 코드로 구분된다
	 */
	private void requireNoKnownThreat(String url) {
		RiskVerdict verdict = riskVerificationService.verify(url).verdict();
		if (verdict == RiskVerdict.THREAT) {
			log.warn("Link write rejected by URL risk check: verdict={}", verdict);
			throw new UnsafeUrlException();
		}
		if (verdict == RiskVerdict.UNKNOWN) {
			log.warn("Link write rejected by URL risk check: verdict={}", verdict);
			throw new UrlRiskCheckFailedException();
		}
		log.debug("Link write URL risk check completed: verdict={}", verdict);
	}

	/**
	 * 익명 링크를 찾고 secret key를 대조한다. 프로젝트에 편입된 링크는 조회 조건에서 빠지므로
	 * 예전 secret key로는 접근할 수 없다.
	 *
	 * <p>없는 코드와 틀린 secret key를 같은 예외로 합치는 것은 의도된 것이다. 구분해 주면
	 * 코드를 훑어 실재하는 링크를 가려낼 수 있다.</p>
	 */
	public Link requireManagedLink(String code, String secretKey) {
		Link link = linkRepository.findByCodeAndProjectIsNull(code).orElseThrow(() -> {
			log.info("Managed link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		if (!secretKeyManager.matches(secretKey, link.getSecretKeyHash())) {
			log.warn("Managed link authentication failed: code={}", code);
			throw new LinkNotFoundException();
		}
		// 삭제된 링크는 @SoftDelete가 조회 단계에서 걸러내므로 여기 도달하지 않는다.
		return link;
	}

	private LinkManagementResponse toManagementResponse(Link link, boolean editable, String shortUrl) {
		return new LinkManagementResponse(link.getCode(), link.getName(), shortUrl == null ? baseUrl + "/" + link.getCode() : shortUrl,
				link.getOriginalUrl(), link.getCampaign() == null ? null : link.getCampaign().getId(), editable, link.getExpiresAt(),
				link.getCreatedAt(), link.getUpdatedAt());
	}

	/**
	 * 프로젝트 링크의 표시용 주소를 만든다. 서브도메인이 설정된 프로젝트는 그 호스트로 붙여야
	 * 실제로 열리는 주소가 된다. 코드 공간이 호스트별로 다르기 때문이다.
	 */
	private String projectShortUrl(Link link) {
		if (link.getSubdomain() == null) return baseUrl + "/" + link.getCode();
		return addresses.origin(link.getSubdomain()) + "/" + link.getCode();
	}

	private void validateUpdateRequest(UpdateLinkRequest request) {
		if (request == null || !request.hasChanges()) {
			throw new IllegalArgumentException("변경할 값을 하나 이상 입력해야 합니다.");
		}
	}

	/**
	 * 코드를 새로 뽑아 저장하고, 유일 제약 위반이면 다시 시도한다.
	 *
	 * <p>{@code saveAndFlush}로 즉시 반영하는 것이 이 구조의 전제다. 지연시키면 제약 위반이
	 * 트랜잭션 커밋 시점에야 터져 여기서 잡아 재시도할 수 없다.</p>
	 *
	 * @throws IllegalStateException 재시도 한도까지 모두 충돌한 경우
	 */
	private Link saveWithUniqueCode(String originalUrl, String secretKeyHash, Instant expiresAt) {
		for (int attempt = 1; attempt <= MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = linkCodeGenerator.generate();
			Link link = Link.create(code, originalUrl, secretKeyHash, expiresAt);
			try {
				return linkRepository.saveAndFlush(link);
			} catch (DataIntegrityViolationException exception) {
				metrics.recordLinkCodeGeneration("collision");
				log.debug("Generated link code collision: attempt={}, code={}", attempt, code);
			}
		}
		metrics.recordLinkCodeGeneration("exhausted");
		log.error("Link code generation exhausted: attempts={}", MAX_CODE_GENERATION_ATTEMPTS);
		throw new IllegalStateException("단축 코드를 생성하지 못했습니다.");
	}

	/**
	 * 만료 시각은 없거나 미래여야 한다. 과거 시각을 허용하면 만들자마자 410이 되는 링크가 생긴다.
	 */
	private void validateExpiration(Instant expiresAt) {
		if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
			throw new IllegalArgumentException("만료 시각은 현재보다 미래여야 합니다.");
		}
	}

	private String removeTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
