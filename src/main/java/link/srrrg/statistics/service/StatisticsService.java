package link.srrrg.statistics.service;

import link.srrrg.link.repository.LinkRepository;
import link.srrrg.project.membership.model.ProjectMember;
import link.srrrg.project.membership.model.ProjectRole;
import link.srrrg.project.membership.service.ProjectAccessService;
import link.srrrg.statistics.dto.StatisticsResponse;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import link.srrrg.campaign.model.Campaign;
import link.srrrg.campaign.model.CampaignNotFoundException;
import link.srrrg.campaign.repository.CampaignRepository;
import link.srrrg.link.model.Link;
import link.srrrg.link.model.LinkNotFoundException;
import link.srrrg.link.repository.LinkRepository;
import link.srrrg.link.management.service.LinkManagementService;
import link.srrrg.project.apikey.model.ApiKeyPrincipal;
import link.srrrg.project.membership.service.ProjectAccessService;
import link.srrrg.project.membership.model.ProjectMember;
import link.srrrg.project.membership.model.ProjectRole;
import link.srrrg.statistics.dto.StatisticsResponse.Bucket;

/**
 * 통계 조회 유스케이스의 application 경계다. API 표면별 인증 결과를 받아 조회 대상의 소유 범위를
 * 확인하고, 검증된 식별자만 {@link StatisticsReportService}에 전달한다.
 *
 * <p>Controller가 Repository와 권한 정책을 직접 소유하지 않도록 링크·캠페인 조회와 프로젝트 멤버십
 * 판정을 이 클래스에 모았다. 실제 기간 계산과 집계 조립은 {@code StatisticsReportService}가 맡으므로,
 * 접근 정책 변경과 통계 계산 변경은 서로 독립적으로 진행할 수 있다.</p>
 */
@Service
public class StatisticsService {
	private final StatisticsReportService reports;
	private final LinkManagementService linkManagementService;
	private final LinkRepository links;
	private final CampaignRepository campaigns;
	private final ProjectAccessService projectAccessService;

	public StatisticsService(StatisticsReportService reports, LinkManagementService linkManagementService,
			LinkRepository links, CampaignRepository campaigns, ProjectAccessService projectAccessService) {
		this.reports = reports;
		this.linkManagementService = linkManagementService;
		this.links = links;
		this.campaigns = campaigns;
		this.projectAccessService = projectAccessService;
	}

	/**
	 * secret key로 익명 링크 소유를 확인한 뒤 링크 단위 통계를 조회한다. 링크 인증과 식별자 획득을
	 * 한 번의 조회로 끝내므로 인증 직후 같은 코드를 다시 조회하지 않는다.
	 */
	public StatisticsResponse anonymousLink(String code, String secretKey, LocalDate from, LocalDate to,
			Bucket bucket, int offset, int limit) {
		Link link = linkManagementService.requireManagedLink(code, secretKey);
		return reports.link(link.getId(), code, from, to, bucket, offset, limit);
	}

	public StatisticsResponse webProject(Long userId, Long projectId, LocalDate from, LocalDate to,
			Bucket bucket, int offset, int limit) {
		ProjectMember member = requireMember(userId, projectId);
		return reports.project(projectId, member.getProject().getName(), from, to, bucket, offset, limit);
	}

	/**
	 * 캠페인을 먼저 조회해 소유 프로젝트를 확인한다. 경로에 프로젝트 id가 없으므로 이 순서를 바꾸면
	 * 사용자 권한을 판정할 기준이 사라진다.
	 */
	public StatisticsResponse webCampaign(Long userId, Long campaignId, LocalDate from, LocalDate to,
			Bucket bucket, int offset, int limit) {
		Campaign campaign = campaign(campaignId);
		requireMember(userId, campaign.getProject().getId());
		return reports.campaign(campaignId, campaign.getName(), from, to, bucket, offset, limit);
	}

	public StatisticsResponse webProjectLink(Long userId, Long projectId, String code, LocalDate from, LocalDate to,
			Bucket bucket, int offset, int limit) {
		requireMember(userId, projectId);
		Link link = projectLink(projectId, code);
		return reports.link(link.getId(), code, from, to, bucket, offset, limit);
	}

	/**
	 * Controller의 공통 API key 경계에서 프로젝트 일치와 {@code stats:read} scope가 검증된 뒤 호출된다.
	 * 기존 계약대로 별도 프로젝트 조회 없이 안정적인 표시 이름을 만든다.
	 */
	public StatisticsResponse publicProject(ApiKeyPrincipal principal, Long projectId, LocalDate from, LocalDate to,
			Bucket bucket, int offset, int limit) {
		return reports.project(projectId, "project-" + principal.projectId(), from, to, bucket, offset, limit);
	}

	/**
	 * 캠페인 경로에는 프로젝트 id가 없으므로 대상 조회 후 API key의 프로젝트와 직접 대조한다.
	 * 다른 프로젝트의 캠페인이면 통계 쿼리를 실행하기 전에 접근을 거부한다.
	 */
	public StatisticsResponse publicCampaign(ApiKeyPrincipal principal, Long campaignId, LocalDate from, LocalDate to,
			Bucket bucket, int offset, int limit) {
		Campaign campaign = campaign(campaignId);
		if (!campaign.getProject().getId().equals(principal.projectId())) {
			throw new SecurityException("프로젝트 접근 권한이 없습니다.");
		}
		return reports.campaign(campaignId, campaign.getName(), from, to, bucket, offset, limit);
	}

	public StatisticsResponse publicProjectLink(Long projectId, String code, LocalDate from, LocalDate to,
			Bucket bucket, int offset, int limit) {
		Link link = projectLink(projectId, code);
		return reports.link(link.getId(), code, from, to, bucket, offset, limit);
	}

	private ProjectMember requireMember(Long userId, Long projectId) {
		return projectAccessService.requireRole(userId, projectId, ProjectRole.VIEWER);
	}

	private Link projectLink(Long projectId, String code) {
		return links.findByProjectIdAndCode(projectId, code).orElseThrow(LinkNotFoundException::new);
	}

	private Campaign campaign(Long campaignId) {
		return campaigns.findById(campaignId).orElseThrow(CampaignNotFoundException::new);
	}
}
