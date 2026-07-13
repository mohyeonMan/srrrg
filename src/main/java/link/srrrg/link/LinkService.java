package link.srrrg.link;

import java.time.Instant;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.link.SecretKeyManager.GeneratedSecretKey;
import link.srrrg.link.access.ClientRequestInfo;
import link.srrrg.link.access.RedirectCheckRecorder;
import link.srrrg.link.dto.CreateLinkRequest;
import link.srrrg.link.dto.CreateLinkResponse;
import link.srrrg.link.dto.DeleteLinkResponse;
import link.srrrg.link.dto.LinkManagementResponse;
import link.srrrg.link.dto.LinkStatisticsSummary;
import link.srrrg.link.dto.RedirectCheckResponse;
import link.srrrg.link.dto.RedirectLink;
import link.srrrg.link.dto.UpdateLinkRequest;
import link.srrrg.link.risk.UrlRiskCheckResult;
import link.srrrg.link.risk.UrlRiskChecker;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LinkService {

	private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

	private final LinkRepository linkRepository;
	private final LinkCodeGenerator linkCodeGenerator;
	private final SecretKeyManager secretKeyManager;
	private final UrlValidator urlValidator;
	private final UrlRiskChecker urlRiskChecker;
	private final RedirectCheckRecorder redirectCheckRecorder;
	private final String baseUrl;
	private final Duration verificationTtl;

	public LinkService(LinkRepository linkRepository, LinkCodeGenerator linkCodeGenerator,
			SecretKeyManager secretKeyManager, UrlValidator urlValidator,
			UrlRiskChecker urlRiskChecker, RedirectCheckRecorder redirectCheckRecorder,
			@Value("${srrrg.base-url}") String baseUrl,
			@Value("${srrrg.redirect.verification-ttl:1h}") Duration verificationTtl) {
		this.linkRepository = linkRepository;
		this.linkCodeGenerator = linkCodeGenerator;
		this.secretKeyManager = secretKeyManager;
		this.urlValidator = urlValidator;
		this.urlRiskChecker = urlRiskChecker;
		this.redirectCheckRecorder = redirectCheckRecorder;
		this.baseUrl = removeTrailingSlash(baseUrl);
		this.verificationTtl = verificationTtl;
	}

	public CreateLinkResponse create(CreateLinkRequest request) {
		log.debug("Link creation started: expiresAtPresent={}", request.expiresAt() != null);
		urlValidator.validate(request.originalUrl());
		validateExpiration(request.expiresAt());
		// 신규 링크는 위협 미탐지 결과가 있어야만 저장함.
		requireNoKnownThreat(request.originalUrl());

		String code = generateUniqueCode();
		GeneratedSecretKey secretKey = secretKeyManager.generate();
		Link link = Link.create(code, request.originalUrl(), secretKey.hash(), request.expiresAt());
		link.updateVerification(LinkStatus.NO_THREAT_FOUND, Instant.now());
		Link savedLink = linkRepository.save(link);
		log.info("Link created: code={}, expiresAtPresent={}", savedLink.getCode(), savedLink.getExpiresAt() != null);
		return new CreateLinkResponse(savedLink.getCode(), baseUrl + "/" + savedLink.getCode(),
				secretKey.value(), savedLink.getExpiresAt());
	}

	@Transactional(readOnly = true)
	public LinkManagementResponse getManagedLink(String code, String secretKey) {
		return toManagementResponse(findManagedLink(code, secretKey));
	}

	public LinkManagementResponse updateManagedLink(String code, String secretKey, UpdateLinkRequest request) {
		// 외부 검사 시간 동안 DB 트랜잭션을 유지하지 않고 검사가 끝난 뒤 저장함.
		log.debug("Managed link update started: code={}", code);
		Link link = findManagedLink(code, secretKey);
		validateUpdateRequest(request);
		boolean urlChanged = request.isOriginalUrlPresent()
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
			link.updateVerification(LinkStatus.NO_THREAT_FOUND, Instant.now());
		}
		if (request.isExpiresAtPresent()) {
			link.updateExpiresAt(request.getExpiresAt());
		}
		Link savedLink = linkRepository.save(link);
		log.info("Managed link updated: code={}, originalUrlChanged={}, expiresAtChanged={}",
				code, urlChanged, request.isExpiresAtPresent());
		return toManagementResponse(savedLink);
	}

	@Transactional
	public DeleteLinkResponse deleteManagedLink(String code, String secretKey) {
		findManagedLink(code, secretKey).delete();
		log.info("Managed link deleted: code={}", code);
		return new DeleteLinkResponse(true);
	}

	public RedirectLink resolveRedirectPage(String code, ClientRequestInfo requestInfo) {
		log.debug("Secure Redirect page requested: code={}", code);
		Link link = findAvailableLink(code);
		urlValidator.validate(link.getOriginalUrl());
		LinkStatus cachedStatus = null;
		// 최근 검사 결과가 유효하면 검사 API 호출 없이 페이지에서 해당 결과를 재사용함.
		if (hasFreshVerification(link)) {
			cachedStatus = redirectCheckRecorder.reuseCachedCheck(link.getCode(), link.getOriginalUrl(),
					link.getStatus(), link.getVerifiedAt(), requestInfo).orElse(null);
		}
		log.debug("Secure Redirect page resolved: code={}, cachedVerification={}", code, cachedStatus != null);
		return new RedirectLink(link.getCode(), link.getOriginalUrl(), cachedStatus);
	}

	public RedirectCheckResponse checkRedirect(String code, ClientRequestInfo requestInfo) {
		long startedAt = System.nanoTime();
		log.info("Redirect risk check started: code={}", code);
		Link initialLink = findAvailableLink(code);
		String checkedUrl = initialLink.getOriginalUrl();
		urlValidator.validate(checkedUrl);
		UrlRiskCheckResult result = urlRiskChecker.check(checkedUrl);

		// 외부 API 호출 중 URL이 변경됐는지 다시 확인함.
		Link currentLink = findAvailableLink(code);
		if (!checkedUrl.equals(currentLink.getOriginalUrl())) {
			log.warn("Redirect risk check invalidated: reason=URL_CHANGED, code={}, elapsedMs={}",
					code, elapsedMillis(startedAt));
			return new RedirectCheckResponse(UrlRiskCheckResult.CHECK_FAILED, null);
		}
		urlValidator.validate(currentLink.getOriginalUrl());
		// 검사 결과 저장과 성공 시 통계 기록을 하나의 트랜잭션으로 처리함.
		boolean checkRecorded = redirectCheckRecorder.recordCheck(currentLink, checkedUrl, result, requestInfo);
		if (!checkRecorded) {
			log.warn("Redirect risk check invalidated while recording: reason=URL_CHANGED, code={}, elapsedMs={}",
					code, elapsedMillis(startedAt));
			return new RedirectCheckResponse(UrlRiskCheckResult.CHECK_FAILED, null);
		}

		if (result == UrlRiskCheckResult.NO_THREAT_FOUND) {
			log.info("Redirect risk check completed: code={}, result={}, accessRecorded=true, elapsedMs={}",
					code, result, elapsedMillis(startedAt));
			return new RedirectCheckResponse(result, checkedUrl);
		}
		if (result == UrlRiskCheckResult.CHECK_FAILED) {
			// 검사 실패 시 현재 저장 URL을 반환해 사용자가 수동 이동을 선택할 수 있게 함.
			log.warn("Redirect risk check completed: code={}, result={}, manualRedirectAvailable=true, elapsedMs={}",
					code, result, elapsedMillis(startedAt));
			return new RedirectCheckResponse(result, checkedUrl);
		}
		// 위협 탐지 결과에는 목적지 URL을 포함하지 않음.
		log.warn("Redirect risk check completed: code={}, result={}, redirectBlocked=true, elapsedMs={}",
				code, result, elapsedMillis(startedAt));
		return new RedirectCheckResponse(result, null);
	}

	private void requireNoKnownThreat(String url) {
		UrlRiskCheckResult result = urlRiskChecker.check(url);
		if (result == UrlRiskCheckResult.THREAT_DETECTED) {
			log.warn("Link write rejected by URL risk check: result={}", result);
			throw new UnsafeUrlException();
		}
		if (result == UrlRiskCheckResult.CHECK_FAILED) {
			log.warn("Link write rejected by URL risk check: result={}", result);
			throw new UrlRiskCheckFailedException();
		}
		log.debug("Link write URL risk check completed: result={}", result);
	}

	private Link findAvailableLink(String code) {
		Link link = linkRepository.findByCode(code).orElseThrow(() -> {
			log.info("Link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		boolean deleted = link.isDeleted();
		boolean expired = link.isExpiredAt(Instant.now());
		if (deleted || expired) {
			log.info("Link unavailable: code={}, deleted={}, expired={}", code, deleted, expired);
			throw new LinkGoneException();
		}
		return link;
	}

	private Link findManagedLink(String code, String secretKey) {
		Link link = linkRepository.findByCode(code).orElseThrow(() -> {
			log.info("Managed link lookup failed: reason=NOT_FOUND, code={}", code);
			return new LinkNotFoundException();
		});
		if (!secretKeyManager.matches(secretKey, link.getSecretKeyHash())) {
			log.warn("Managed link authentication failed: code={}", code);
			throw new LinkNotFoundException();
		}
		if (link.isDeleted()) {
			log.info("Managed link unavailable: reason=DELETED, code={}", code);
			throw new LinkGoneException();
		}
		return link;
	}

	private LinkManagementResponse toManagementResponse(Link link) {
		return new LinkManagementResponse(link.getCode(), baseUrl + "/" + link.getCode(),
				link.getOriginalUrl(), link.getExpiresAt(), link.getStatus(), link.getVerifiedAt(),
				new LinkStatisticsSummary(link.getClickCount(), link.getRedirectCount()),
				link.getCreatedAt(), link.getUpdatedAt());
	}

	private void validateUpdateRequest(UpdateLinkRequest request) {
		if (request == null || !request.hasChanges()) {
			throw new IllegalArgumentException("변경할 값을 하나 이상 입력해야 합니다.");
		}
	}

	private String generateUniqueCode() {
		for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = linkCodeGenerator.generate();
			if (!linkRepository.existsByCode(code)) {
				return code;
			}
			log.debug("Generated link code collision: attempt={}", attempt + 1);
		}
		log.error("Link code generation exhausted: attempts={}", MAX_CODE_GENERATION_ATTEMPTS);
		throw new IllegalStateException("단축 코드를 생성하지 못했습니다.");
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

	private boolean hasFreshVerification(Link link) {
		// NOT_VERIFIED 또는 TTL이 지난 결과는 캐시로 사용하지 않음.
		return link.getStatus() != null
				&& link.getStatus() != LinkStatus.NOT_VERIFIED
				&& link.getVerifiedAt() != null
				&& link.getVerifiedAt().isAfter(Instant.now().minus(verificationTtl));
	}

	private void validateExpiration(Instant expiresAt) {
		if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
			throw new IllegalArgumentException("만료 시각은 현재보다 미래여야 합니다.");
		}
	}

	private String removeTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
