package link.srrrg.campaign.link.csv.service;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.campaign.model.Campaign;
import link.srrrg.common.ratelimit.service.RateLimitService;
import link.srrrg.identity.account.model.User;
import link.srrrg.link.creation.service.LinkCreationService;
import link.srrrg.link.creation.service.UrlValidator;
import link.srrrg.link.model.Link;
import link.srrrg.link.model.LinkUtmValue;
import link.srrrg.link.repository.LinkRepository;
import link.srrrg.link.repository.LinkUtmValueRepository;
import link.srrrg.utmtemplate.model.UtmTemplateField;
import link.srrrg.utmtemplate.repository.UtmTemplateFieldRepository;

/**
 * CSV 업로드와 내려받기를 담당한다. 업로드는 파일 전체를 먼저 검증하고,
 * 문제가 없을 때만 요청 트랜잭션 안에서 링크를 모두 생성한다.
 */
@Service
public class CampaignCsvService {

	private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
	private static final int MAX_DATA_ROWS = 10_000;
	private static final int MAX_EXPORT_ROWS = 10_000;
	private static final String COL_ORIGINAL_URL = "original_url";
	private static final String COL_EXTERNAL_ID = "external_id";
	// Excel이 UTF-8 CSV를 제대로 열려면 BOM이 필요하다. 내려주는 파일마다 앞에 붙이고,
	// 반대로 업로드받은 파일에서는 떼어 낸다. 붙은 채 파싱하면 첫 헤더 이름에 보이지 않는 문자가 섞인다.
	private static final char BOM = '﻿';

	private final LinkCreationService linkCreation;
	private final UrlValidator urlValidator;
	private final UtmTemplateFieldRepository fields;
	private final LinkRepository links;
	private final LinkUtmValueRepository linkUtmValues;
	private final RateLimitService rateLimitService;
	private final String baseUrl;

	public CampaignCsvService(LinkCreationService linkCreation, UrlValidator urlValidator,
			UtmTemplateFieldRepository fields, LinkRepository links, LinkUtmValueRepository linkUtmValues,
			RateLimitService rateLimitService,
			@Value("${srrrg.base-url}") String baseUrl) {
		this.linkCreation = linkCreation;
		this.urlValidator = urlValidator;
		this.fields = fields;
		this.links = links;
		this.linkUtmValues = linkUtmValues;
		this.rateLimitService = rateLimitService;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public String templateCsv(Campaign campaign) {
		List<String> headers = new ArrayList<>(List.of(COL_ORIGINAL_URL, COL_EXTERNAL_ID));
		headers.addAll(activeFieldNames(campaign));
		StringWriter writer = new StringWriter();
		writer.write(BOM);
		try (var printer = new org.apache.commons.csv.CSVPrinter(writer,
				CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
			// 헤더만 제공하고 예시 데이터 행은 넣지 않는다.
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
		return writer.toString();
	}

	/**
	 * 업로드된 CSV 전체를 검증한 뒤 같은 트랜잭션에서 링크를 모두 만든다.
	 * 한 행이라도 검증 또는 저장에 실패하면 요청 전체가 롤백되어 부분 결과를 남기지 않는다.
	 *
	 * @throws IllegalArgumentException 파일이 비었거나 크기·인코딩·헤더·행 수 제한을 어긴 경우
	 */
	@Transactional
	public int createLinks(Campaign campaign, byte[] content, User createdBy, Long createdByApiKeyId) {
		if (content.length == 0) {
			throw new IllegalArgumentException("CSV 파일이 비어 있습니다.");
		}
		if (content.length > MAX_FILE_BYTES) {
			throw new IllegalArgumentException("CSV 파일은 최대 10MB까지 업로드할 수 있습니다.");
		}
		String text = decodeUtf8(content);

		Long projectId = campaign.getProject().getId();
		rateLimitService.checkCsvUpload(projectId);

		List<ParsedRow> rows = parse(text, campaign);
		rateLimitService.checkBulkLinkQuota(projectId, rows.size());
		for (ParsedRow row : rows) {
			linkCreation.createForCampaign(row.originalUrl(), null, campaign.getProject(), createdBy,
					createdByApiKeyId, null, null, campaign, campaign.getUtmTemplate(), row.externalId(), row.utmValues());
		}
		return rows.size();
	}

	/**
	 * 캠페인의 링크를 CSV로 내보낸다. 결과 전체를 메모리에 담으므로 상한을 두고,
	 * 넘으면 잘라 내려주는 대신 오류로 끝낸다. 일부만 담긴 파일을 전부인 것처럼 받으면
	 * 사용자가 누락을 알아차리지 못하기 때문이다.
	 *
	 * <p>상한보다 하나 더 읽어 초과 여부를 판단한다. 별도 count 쿼리를 피하기 위한 방법이다.</p>
	 */
	public String exportLinksCsv(Campaign campaign, Instant createdFrom, Instant createdTo, String externalIdQuery) {
		String externalIdPattern = (externalIdQuery == null || externalIdQuery.isBlank()) ? null
				: "%" + externalIdQuery.trim() + "%";
		Specification<Link> spec = exportSpecification(campaign.getId(), createdFrom, createdTo, externalIdPattern);
		List<Link> found = links.findAll(spec, PageRequest.of(0, MAX_EXPORT_ROWS + 1, Sort.by(Sort.Direction.DESC, "id"))).getContent();
		if (found.size() > MAX_EXPORT_ROWS) {
			throw new IllegalArgumentException("내보낼 링크가 " + MAX_EXPORT_ROWS + "개를 초과합니다. 조회 조건을 좁혀주세요.");
		}
		List<String> utmFieldNames = activeFieldNames(campaign);

		List<String> headers = new ArrayList<>(List.of("code", "short_url", COL_ORIGINAL_URL, COL_EXTERNAL_ID));
		headers.addAll(utmFieldNames);
		headers.add("created_at");

		StringWriter writer = new StringWriter();
		writer.write(BOM);
		try (var printer = new org.apache.commons.csv.CSVPrinter(writer,
				CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
			for (Link link : found) {
				Map<String, String> values = linkUtmValues.findByLinkId(link.getId()).stream()
						.collect(Collectors.toMap(LinkUtmValue::getFieldName, LinkUtmValue::getValue));
				List<Object> record = new ArrayList<>();
				record.add(link.getCode());
				record.add(baseUrl + "/" + link.getCode());
				record.add(link.getOriginalUrl());
				record.add(link.getExternalId() == null ? "" : link.getExternalId());
				for (String fieldName : utmFieldNames) {
					record.add(values.getOrDefault(fieldName, ""));
				}
				record.add(link.getCreatedAt());
				printer.printRecord(record);
			}
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
		return writer.toString();
	}

	private Specification<Link> exportSpecification(Long campaignId, Instant createdFrom, Instant createdTo, String externalIdPattern) {
		return (root, query, criteriaBuilder) -> {
			List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
			// 삭제된 링크는 @SoftDelete가 자동으로 걸러내므로 술어를 따로 걸지 않는다.
			predicates.add(criteriaBuilder.equal(root.get("campaign").get("id"), campaignId));
			if (createdFrom != null) {
				predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
			}
			if (createdTo != null) {
				predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
			}
			if (externalIdPattern != null) {
				predicates.add(criteriaBuilder.like(root.get("externalId"), externalIdPattern));
			}
			return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
		};
	}

	private List<String> activeFieldNames(Campaign campaign) {
		if (campaign.getUtmTemplate() == null) return List.of();
		return fields.findByUtmTemplateIdAndDeletedAtIsNullOrderByNameAsc(campaign.getUtmTemplate().getId())
				.stream().map(UtmTemplateField::getName).toList();
	}

	/**
	 * CSV 전체를 읽어 행 목록으로 바꾼다. 여기서 던지는 예외는 모두 파일 전체를 거절하는 오류다.
	 *
	 * <p>헤더에 활성 UTM 필드가 아닌 컬럼이 있으면 거절한다. 조용히 무시하면 사용자는 값을 넣었다고
	 * 생각하는데 링크에는 반영되지 않는다. 헤더 중복도 거절하는데, 어느 쪽 값을 쓸지 정할 수 없기 때문이다.</p>
	 */
	private List<ParsedRow> parse(String text, Campaign campaign) {
		Set<String> allowedUtmColumns = new HashSet<>(activeFieldNames(campaign));
		CSVParser parser;
		try {
			parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).build()
					.parse(new StringReader(text));
		} catch (IOException exception) {
			throw new IllegalArgumentException("CSV 형식을 읽을 수 없습니다.");
		}

		List<String> headerNames = new ArrayList<>(parser.getHeaderNames());
		if (new HashSet<>(headerNames).size() != headerNames.size()) {
			throw new IllegalArgumentException("CSV 헤더에 중복된 컬럼이 있습니다.");
		}
		if (!headerNames.contains(COL_ORIGINAL_URL) || !headerNames.contains(COL_EXTERNAL_ID)) {
			throw new IllegalArgumentException("CSV 헤더에 original_url, external_id 컬럼이 모두 필요합니다.");
		}
		List<String> utmHeaderNames = new ArrayList<>();
		for (String header : headerNames) {
			if (header.equals(COL_ORIGINAL_URL) || header.equals(COL_EXTERNAL_ID)) continue;
			if (!allowedUtmColumns.contains(header)) {
				throw new IllegalArgumentException("활성 UTM 필드가 아닌 컬럼입니다: " + header);
			}
			utmHeaderNames.add(header);
		}

		List<CSVRecord> records;
		try {
			records = parser.getRecords();
		} catch (java.io.UncheckedIOException exception) {
			throw new IllegalArgumentException("CSV 형식을 읽을 수 없습니다.");
		}
		if (records.isEmpty()) {
			throw new IllegalArgumentException("CSV에 데이터 행이 없습니다.");
		}
		if (records.size() > MAX_DATA_ROWS) {
			throw new IllegalArgumentException("CSV는 최대 " + MAX_DATA_ROWS + "행까지 업로드할 수 있습니다.");
		}

		Set<String> seenExternalIds = new HashSet<>();
		List<ParsedRow> rows = new ArrayList<>(records.size());
		for (CSVRecord record : records) {
			rows.add(parseRow(record, campaign, utmHeaderNames, seenExternalIds));
		}
		Set<String> existingExternalIds = seenExternalIds.isEmpty() ? Set.of()
				: new HashSet<>(links.findExternalIdsByCampaignIdAndExternalIdIn(campaign.getId(), seenExternalIds));
		for (ParsedRow row : rows) {
			if (row.externalId() != null && existingExternalIds.contains(row.externalId())) {
				throw rowError(row.rowNumber(), COL_EXTERNAL_ID, "같은 external_id가 이미 이 캠페인에 사용 중입니다.");
			}
		}
		return rows;
	}

	/**
	 * 한 행을 저장 가능한 형태로 바꾼다. 오류 메시지에는 데이터 행 번호와 컬럼을 넣어
	 * 사용자가 파일에서 바로 문제 위치를 찾을 수 있게 한다.
	 */
	private ParsedRow parseRow(CSVRecord record, Campaign campaign, List<String> utmHeaderNames, Set<String> seenExternalIds) {
		long rowNumber = record.getRecordNumber();
		String originalUrlCell = safeCell(record, COL_ORIGINAL_URL).trim();
		String storedOriginalUrl = originalUrlCell.isEmpty() ? null : originalUrlCell;
		String rawExternalId = safeCell(record, COL_EXTERNAL_ID).trim();
		String storedExternalId = rawExternalId.isEmpty() ? null : rawExternalId;

		if (storedOriginalUrl != null && storedOriginalUrl.length() > 2048) {
			throw rowError(rowNumber, COL_ORIGINAL_URL, "원본 URL은 2,048자 이하여야 합니다.");
		}
		if (storedExternalId != null && storedExternalId.length() > 100) {
			throw rowError(rowNumber, COL_EXTERNAL_ID, "external_id는 100자 이하여야 합니다.");
		}
		if (storedExternalId != null && !seenExternalIds.add(storedExternalId)) {
			throw rowError(rowNumber, COL_EXTERNAL_ID, "CSV 파일 내부에 중복된 external_id가 있습니다.");
		}

		String destination = storedOriginalUrl != null ? storedOriginalUrl : campaign.getDefaultOriginalUrl();
		try {
			urlValidator.validate(destination);
		} catch (IllegalArgumentException exception) {
			throw rowError(rowNumber, COL_ORIGINAL_URL, exception.getMessage());
		}

		Map<String, String> utmValues = new LinkedHashMap<>();
		for (String header : utmHeaderNames) {
			String cell = safeCell(record, header).trim();
			if (cell.length() > 500) {
				throw rowError(rowNumber, header, "UTM 값은 500자 이하여야 합니다.");
			}
			if (!cell.isEmpty()) utmValues.put(header, cell);
		}
		return new ParsedRow(rowNumber, storedOriginalUrl, storedExternalId, utmValues);
	}

	private IllegalArgumentException rowError(long rowNumber, String column, String message) {
		return new IllegalArgumentException("CSV 데이터 " + rowNumber + "행 " + column + ": " + message);
	}

	private String safeCell(CSVRecord record, String column) {
		if (!record.isMapped(column)) return "";
		String value = record.get(column);
		return value == null ? "" : value;
	}

	/**
	 * UTF-8만 허용하고 BOM은 떼어 낸다. 잘못된 바이트를 대체 문자로 바꾸지 않고 실패시키는 것이 중요하다.
	 * 조용히 대체하면 깨진 URL이 그대로 링크로 만들어져 나중에 원인을 찾기 어렵다.
	 */
	private String decodeUtf8(byte[] content) {
		byte[] withoutBom = content;
		if (content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
			withoutBom = new byte[content.length - 3];
			System.arraycopy(content, 3, withoutBom, 0, withoutBom.length);
		}
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(java.nio.ByteBuffer.wrap(withoutBom))
					.toString();
		} catch (CharacterCodingException exception) {
			throw new IllegalArgumentException("CSV 파일은 UTF-8 또는 UTF-8 BOM 인코딩만 지원합니다.");
		}
	}

	private record ParsedRow(long rowNumber, String originalUrl, String externalId, Map<String, String> utmValues) { }
}
