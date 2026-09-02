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

/**
 * CSV 업로드와 내려받기를 담당한다. 업로드는 파싱과 행 저장까지만 하고 실제 링크 생성은
 * {@link CampaignImportWorker}가 비동기로 처리한다.
 *
 * <p>동기로 만들지 않은 것은 만 건까지 받을 수 있기 때문이다. 요청 안에서 전부 만들면 HTTP 타임아웃과
 * 긴 트랜잭션이 생기고, 중간에 끊기면 어디까지 처리됐는지 알 수 없다. 대신 행을 먼저 저장해 두면
 * 진행률을 보여줄 수 있고 파드가 죽어도 이어서 처리된다.</p>
 *
 * <p>파싱 단계에서 걸러낸 오류는 예외로 올리지 않고 행에 미리 실패 표시를 해 둔다. 한 행의 형식 오류로
 * 파일 전체를 거절하지 않기 위해서다. 다만 파일 수준 오류(헤더 불일치, 인코딩, 파일 내 external_id 중복)는
 * 전체를 거절한다. 고치지 않으면 결과 전체가 의미를 잃기 때문이다.</p>
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

	/**
	 * 업로드된 CSV를 검증해 임포트 작업과 행을 만든다. 링크는 여기서 만들지 않는다.
	 *
	 * <p>멱등 키를 필수로 받는다. 큰 파일 업로드는 타임아웃으로 응답을 놓치기 쉬운데, 그때 재시도가
	 * 같은 파일을 두 번 임포트하면 링크가 두 배로 생긴다. 파일 내용의 해시를 함께 저장해
	 * 같은 키로 다른 파일을 올린 경우와 구분한다.</p>
	 *
	 * <p>검사 순서에 의도가 있다. 기존 작업을 먼저 확인해 재시도를 걸러낸 뒤에 레이트리밋과 할당량을
	 * 소비하므로, 재시도가 할당량을 깎지 않는다. 할당량은 실제 파싱된 행 수만큼 소비한다.</p>
	 *
	 * @throws CampaignImportIdempotencyConflictException 같은 키로 다른 파일을 올린 경우
	 * @throws ActiveImportConflictException 같은 프로젝트에 진행 중인 임포트가 이미 있는 경우
	 * @throws IllegalArgumentException 파일이 비었거나 크기·인코딩·헤더·행 수 제한을 어긴 경우
	 */
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
			// 프로젝트당 동시 임포트 하나 제약. 사전 확인이 아니라 DB 제약으로 막는 이유는
			// 여러 파드에서 동시에 업로드가 들어올 수 있기 때문이다.
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
			// 파싱 단계에서 이미 실패로 판정된 행은 UTM 값을 저장하지 않고 실패로 확정한다.
			// worker가 집어 갈 일이 없으므로 여기서 집계도 함께 반영한다.
			if (row.preFailureCode() != null) {
				savedRow.fail(row.preFailureCode(), row.preFailureMessage());
				campaignImport.recordRowResult(false);
				continue;
			}
			for (Map.Entry<String, String> entry : row.utmValues().entrySet()) {
				importRowUtmValues.save(CampaignImportRowUtmValue.create(savedRow, entry.getKey(), entry.getValue()));
			}
		}
		return campaignImport;
	}

	/**
	 * 실패한 행만 모아 원본과 같은 형태의 CSV로 만든다. 오류 코드와 메시지를 뒤에 덧붙이므로,
	 * 사용자가 그 파일에서 문제를 고친 뒤 두 열을 지우고 그대로 다시 올릴 수 있다.
	 */
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
						.collect(Collectors.toMap(CampaignImportRowUtmValue::getFieldName, CampaignImportRowUtmValue::getValue));
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
		return rows;
	}

	/**
	 * 한 행을 읽어 저장할 형태로 만든다. 행 단위 오류는 예외가 아니라 미리 실패 표시로 남긴다.
	 *
	 * <p>길이를 넘긴 값을 잘라서 저장하는 것은 실패 CSV에 원본 흔적을 남기기 위해서다.
	 * 어차피 링크는 만들어지지 않으므로 잘린 값이 쓰이지는 않는다.</p>
	 *
	 * <p>파일 안의 external_id 중복만은 예외로 올려 파일 전체를 거절한다. 이 값은 외부 시스템에서
	 * 링크를 되찾는 열쇠라, 어느 행이 살아남을지 알 수 없는 상태로 절반만 처리되면 안 된다.</p>
	 */
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

		Map<String, String> resolved = Map.of();
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

	/**
	 * 업로드 파일의 지문. 같은 멱등 키로 다른 파일을 올렸는지 가리는 데만 쓴다.
	 */
	private String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/**
	 * 제약 이름 문자열로 위반 원인을 구분한다. JDBC 표준으로는 어떤 제약이 걸렸는지 알 수 없어 택한 방법이라,
	 * migration에서 제약 이름을 바꾸면 이 판정이 조용히 빗나가 원인이 다른 예외로 나간다.
	 */
	private boolean constraintNameContains(DataIntegrityViolationException exception, String constraintName) {
		Throwable cause = exception.getMostSpecificCause();
		return cause != null && cause.getMessage() != null && cause.getMessage().contains(constraintName);
	}

	private record ParsedRow(String originalUrl, String externalId, Map<String, String> utmValues,
			String preFailureCode, String preFailureMessage) { }
}
