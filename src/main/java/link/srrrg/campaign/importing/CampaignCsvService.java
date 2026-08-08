package link.srrrg.campaign.importing;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import link.srrrg.campaign.Campaign;
import link.srrrg.campaign.CampaignLinkCreationService;
import link.srrrg.campaign.UtmTemplateField;
import link.srrrg.campaign.UtmTemplateFieldRepository;
import link.srrrg.identity.User;
import link.srrrg.link.Link;
import link.srrrg.link.LinkRepository;
import link.srrrg.link.LinkUtmValue;
import link.srrrg.link.LinkUtmValueRepository;

@Service
public class CampaignCsvService {

	private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
	private static final int MAX_DATA_ROWS = 10_000;
	private static final int MAX_EXPORT_ROWS = 10_000;
	private static final String COL_ORIGINAL_URL = "original_url";
	private static final String COL_EXTERNAL_ID = "external_id";
	private static final char BOM = '﻿';

	private final CampaignLinkCreationService linkCreation;
	private final UtmTemplateFieldRepository fields;
	private final CampaignImportRepository imports;
	private final CampaignImportRowRepository importRows;
	private final CampaignImportRowUtmValueRepository importRowUtmValues;
	private final LinkRepository links;
	private final LinkUtmValueRepository linkUtmValues;
	private final link.srrrg.common.ratelimit.RateLimitService rateLimitService;
	private final String baseUrl;

	public CampaignCsvService(CampaignLinkCreationService linkCreation, UtmTemplateFieldRepository fields,
			CampaignImportRepository imports, CampaignImportRowRepository importRows,
			CampaignImportRowUtmValueRepository importRowUtmValues, LinkRepository links, LinkUtmValueRepository linkUtmValues,
			link.srrrg.common.ratelimit.RateLimitService rateLimitService,
			@Value("${srrrg.base-url}") String baseUrl) {
		this.linkCreation = linkCreation;
		this.fields = fields;
		this.imports = imports;
		this.importRows = importRows;
		this.importRowUtmValues = importRowUtmValues;
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

	@Transactional
	public CampaignImport startImport(Campaign campaign, byte[] content, String idempotencyKey, User createdBy, Long createdByApiKeyId) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("CSV 업로드에는 Idempotency-Key가 필수입니다.");
		}
		if (content.length == 0) {
			throw new IllegalArgumentException("CSV 파일이 비어 있습니다.");
		}
		if (content.length > MAX_FILE_BYTES) {
			throw new IllegalArgumentException("CSV 파일은 최대 10MB까지 업로드할 수 있습니다.");
		}
		String text = decodeUtf8(content);
		String requestHash = sha256(content);

		Optional<CampaignImport> existing = imports.findByCampaignIdAndIdempotencyKey(campaign.getId(), idempotencyKey);
		if (existing.isPresent()) {
			if (!existing.get().getIdempotencyRequestHash().equals(requestHash)) {
				throw new CampaignImportIdempotencyConflictException();
			}
			return existing.get();
		}

		Long projectId = campaign.getProject().getId();
		rateLimitService.checkCsvUpload(projectId);

		List<ParsedRow> rows = parse(text, campaign);
		rateLimitService.checkBulkLinkQuota(projectId, rows.size());

		CampaignImport campaignImport;
		try {
			campaignImport = imports.saveAndFlush(CampaignImport.create(campaign, campaign.getProject(), campaign.getUtmTemplate(),
					rows.size(), idempotencyKey, requestHash, createdBy, createdByApiKeyId));
		} catch (DataIntegrityViolationException exception) {
			if (constraintNameContains(exception, "uq_campaign_imports_active_project")) {
				throw new ActiveImportConflictException();
			}
			if (constraintNameContains(exception, "uq_campaign_imports_campaign_idempotency")) {
				throw new CampaignImportIdempotencyConflictException();
			}
			throw exception;
		}

		int rowNumber = 1;
		for (ParsedRow row : rows) {
			CampaignImportRow savedRow = importRows.save(CampaignImportRow.create(campaignImport, rowNumber++, row.originalUrl(), row.externalId()));
			if (row.preFailureCode() != null) {
				savedRow.fail(row.preFailureCode(), row.preFailureMessage());
				campaignImport.recordRowResult(false);
				continue;
			}
			Long templateId = campaign.getUtmTemplate() == null ? null : campaign.getUtmTemplate().getId();
			for (Map.Entry<UtmTemplateField, String> entry : row.utmValues().entrySet()) {
				importRowUtmValues.save(CampaignImportRowUtmValue.create(savedRow, entry.getKey(), templateId, entry.getValue()));
			}
		}
		return campaignImport;
	}

	public String errorCsv(CampaignImport campaignImport) {
		List<CampaignImportRow> failed = importRows.findByCampaignImportIdAndStatusOrderByRowNumberAsc(campaignImport.getId(), ImportRowStatus.FAILED);
		List<String> utmFieldNames = campaignImport.getUtmTemplate() == null ? List.of()
				: fields.findByUtmTemplateIdOrderByNameAsc(campaignImport.getUtmTemplate().getId()).stream().map(UtmTemplateField::getName).toList();

		List<String> headers = new ArrayList<>(List.of("row_number", COL_ORIGINAL_URL, COL_EXTERNAL_ID));
		headers.addAll(utmFieldNames);
		headers.add("error_code");
		headers.add("error_message");

		StringWriter writer = new StringWriter();
		writer.write(BOM);
		try (var printer = new org.apache.commons.csv.CSVPrinter(writer,
				CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
			for (CampaignImportRow row : failed) {
				Map<String, String> values = importRowUtmValues.findByImportRowId(row.getId()).stream()
						.collect(Collectors.toMap(value -> value.getField().getName(), CampaignImportRowUtmValue::getValue));
				List<Object> record = new ArrayList<>();
				record.add(row.getRowNumber());
				record.add(row.getOriginalUrl());
				record.add(row.getExternalId() == null ? "" : row.getExternalId());
				for (String fieldName : utmFieldNames) {
					record.add(values.getOrDefault(fieldName, ""));
				}
				record.add(row.getErrorCode());
				record.add(row.getErrorMessage());
				printer.printRecord(record);
			}
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
		return writer.toString();
	}

	public String exportLinksCsv(Campaign campaign, Instant createdFrom, Instant createdTo, String externalIdQuery) {
		String externalIdPattern = (externalIdQuery == null || externalIdQuery.isBlank()) ? null
				: "%" + externalIdQuery.trim() + "%";
		Specification<Link> spec = exportSpecification(campaign.getId(), createdFrom, createdTo, externalIdPattern);
		List<Link> found = links.findAll(spec, PageRequest.of(0, MAX_EXPORT_ROWS + 1, Sort.by(Sort.Direction.DESC, "id"))).getContent();
		if (found.size() > MAX_EXPORT_ROWS) {
			throw new IllegalArgumentException("내보낼 링크가 " + MAX_EXPORT_ROWS + "개를 초과합니다. 조회 조건을 좁혀주세요.");
		}
		List<String> utmFieldNames = campaign.getUtmTemplate() == null ? List.of()
				: fields.findByUtmTemplateIdOrderByNameAsc(campaign.getUtmTemplate().getId()).stream().map(UtmTemplateField::getName).toList();

		List<String> headers = new ArrayList<>(List.of("code", "short_url", COL_ORIGINAL_URL, COL_EXTERNAL_ID));
		headers.addAll(utmFieldNames);
		headers.add("created_at");

		StringWriter writer = new StringWriter();
		writer.write(BOM);
		try (var printer = new org.apache.commons.csv.CSVPrinter(writer,
				CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
			for (Link link : found) {
				Map<String, String> values = linkUtmValues.findByLinkId(link.getId()).stream()
						.collect(Collectors.toMap(value -> value.getField().getName(), LinkUtmValue::getValue));
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
			predicates.add(criteriaBuilder.equal(root.get("campaign").get("id"), campaignId));
			predicates.add(criteriaBuilder.isFalse(root.get("deleted")));
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
		return rows;
	}

	private ParsedRow parseRow(CSVRecord record, Campaign campaign, List<String> utmHeaderNames, Set<String> seenExternalIds) {
		String originalUrlCell = safeCell(record, COL_ORIGINAL_URL).trim();
		String storedOriginalUrl = originalUrlCell.isEmpty() ? null : originalUrlCell;
		String rawExternalId = safeCell(record, COL_EXTERNAL_ID).trim();
		String storedExternalId = rawExternalId.isEmpty() ? null : rawExternalId;

		String preFailCode = null;
		String preFailMessage = null;
		if (storedOriginalUrl != null && storedOriginalUrl.length() > 2048) {
			preFailCode = "URL_TOO_LONG";
			preFailMessage = "원본 URL은 2,048자 이하여야 합니다.";
			storedOriginalUrl = storedOriginalUrl.substring(0, 2048);
		}
		if (storedExternalId != null && storedExternalId.length() > 100) {
			preFailCode = preFailCode == null ? "EXTERNAL_ID_TOO_LONG" : preFailCode;
			preFailMessage = preFailMessage == null ? "external_id는 100자 이하여야 합니다." : preFailMessage;
			storedExternalId = storedExternalId.substring(0, 100);
		}
		if (storedExternalId != null && preFailCode == null && !seenExternalIds.add(storedExternalId)) {
			throw new IllegalArgumentException("CSV 파일 내부에 중복된 external_id가 있습니다: " + storedExternalId
					+ " (행 " + record.getRecordNumber() + ")");
		}

		Map<UtmTemplateField, String> resolved = Map.of();
		if (preFailCode == null) {
			Map<String, String> rawValues = new LinkedHashMap<>();
			for (String header : utmHeaderNames) {
				String cell = safeCell(record, header).trim();
				if (!cell.isEmpty()) rawValues.put(header, cell);
			}
			try {
				resolved = linkCreation.resolveUtmValues(campaign.getUtmTemplate(), rawValues);
			} catch (IllegalArgumentException exception) {
				preFailCode = "INVALID_UTM_VALUE";
				preFailMessage = exception.getMessage();
			}
		}
		return new ParsedRow(storedOriginalUrl, storedExternalId, resolved, preFailCode, preFailMessage);
	}

	private String safeCell(CSVRecord record, String column) {
		if (!record.isMapped(column)) return "";
		String value = record.get(column);
		return value == null ? "" : value;
	}

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

	private String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private boolean constraintNameContains(DataIntegrityViolationException exception, String constraintName) {
		Throwable cause = exception.getMostSpecificCause();
		return cause != null && cause.getMessage() != null && cause.getMessage().contains(constraintName);
	}

	private record ParsedRow(String originalUrl, String externalId, Map<UtmTemplateField, String> utmValues,
			String preFailureCode, String preFailureMessage) { }
}
