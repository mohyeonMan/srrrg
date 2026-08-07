package link.srrrg.campaign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.campaign.dto.CreateCampaignLinkRequest;
import link.srrrg.common.ratelimit.RateLimitService;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;

/**
 * JSON batch 링크 생성. 요청 전체를 하나의 트랜잭션으로 처리하며 일부 항목만 성공하는 부분 성공은 없다.
 * idempotency는 batch 전체 단위로 다루고 개별 링크 단위 idempotency-key는 사용하지 않는다.
 */
@Service
public class CampaignLinkBatchService {

	public static final int MAX_BATCH_SIZE = 500;

	private final CampaignService campaignService;
	private final CampaignLinkCreationService linkCreation;
	private final CampaignLinkBatchRepository batches;
	private final CampaignLinkBatchItemRepository batchItems;
	private final LinkRepository links;
	private final RateLimitService rateLimitService;

	public CampaignLinkBatchService(CampaignService campaignService, CampaignLinkCreationService linkCreation,
			CampaignLinkBatchRepository batches, CampaignLinkBatchItemRepository batchItems, LinkRepository links,
			RateLimitService rateLimitService) {
		this.campaignService = campaignService;
		this.linkCreation = linkCreation;
		this.batches = batches;
		this.batchItems = batchItems;
		this.links = links;
		this.rateLimitService = rateLimitService;
	}

	@Transactional
	public List<Link> createBatch(Long apiKeyId, Long projectId, Long campaignId, String idempotencyKey,
			List<CreateCampaignLinkRequest> items) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("batch 요청에는 Idempotency-Key가 필수입니다.");
		}
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("batch 요청에는 최소 1개의 링크가 필요합니다.");
		}
		if (items.size() > MAX_BATCH_SIZE) {
			throw new IllegalArgumentException("batch 요청은 한 번에 최대 " + MAX_BATCH_SIZE + "개까지 가능합니다.");
		}

		Campaign campaign = campaignService.findForApiKey(projectId, campaignId);
		String requestHash = fingerprint(items);

		Optional<CampaignLinkBatch> existing = batches.findByApiKeyIdAndIdempotencyKey(apiKeyId, idempotencyKey);
		if (existing.isPresent()) {
			if (!existing.get().getRequestHash().equals(requestHash)) {
				throw new BatchIdempotencyConflictException();
			}
			return linksForBatch(existing.get().getId());
		}

		rateLimitService.checkJsonBatch(projectId);
		rateLimitService.checkBulkLinkQuota(projectId, items.size());

		List<Link> created = new ArrayList<>(items.size());
		for (CreateCampaignLinkRequest item : items) {
			created.add(linkCreation.createWithinBatch(campaign, item));
		}

		CampaignLinkBatch batch;
		try {
			batch = batches.saveAndFlush(CampaignLinkBatch.create(campaign, apiKeyId, idempotencyKey, requestHash));
		} catch (DataIntegrityViolationException exception) {
			// 동시에 같은 key로 들어온 다른 요청이 먼저 커밋되었다. 이 트랜잭션은 롤백되고, 재시도 시 기존 결과를 반환한다.
			throw new BatchIdempotencyConflictException();
		}
		int index = 0;
		for (Link link : created) {
			batchItems.save(CampaignLinkBatchItem.create(batch, index++, link.getId()));
		}
		return created;
	}

	private List<Link> linksForBatch(Long batchId) {
		List<Long> linkIds = batchItems.findByBatchIdOrderByIdItemIndexAsc(batchId).stream()
				.map(CampaignLinkBatchItem::getLinkId)
				.toList();
		List<Link> found = links.findAllById(linkIds);
		java.util.Map<Long, Link> byId = new java.util.LinkedHashMap<>();
		for (Link link : found) {
			byId.put(link.getId(), link);
		}
		List<Link> ordered = new ArrayList<>(linkIds.size());
		for (Long id : linkIds) {
			ordered.add(byId.get(id));
		}
		return ordered;
	}

	private String fingerprint(List<CreateCampaignLinkRequest> items) {
		StringBuilder combined = new StringBuilder();
		for (CreateCampaignLinkRequest item : items) {
			combined.append(linkCreation.requestFingerprint(item)).append('|');
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(combined.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
