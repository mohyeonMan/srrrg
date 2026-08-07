package link.srrrg.campaign.importing;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign_import_rows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignImportRow {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "import_id", nullable = false)
	private CampaignImport campaignImport;

	@Column(name = "row_number", nullable = false)
	private int rowNumber;

	@Column(name = "original_url", nullable = false, length = 2048)
	private String originalUrl;

	@Column(name = "external_id", length = 100)
	private String externalId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ImportRowStatus status;

	@Column(name = "link_id")
	private Long linkId;

	@Column(name = "error_code", length = 50)
	private String errorCode;
	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "processed_at")
	private Instant processedAt;

	private CampaignImportRow(CampaignImport campaignImport, int rowNumber, String originalUrl, String externalId) {
		this.campaignImport = campaignImport;
		this.rowNumber = rowNumber;
		this.originalUrl = originalUrl;
		this.externalId = externalId;
		this.status = ImportRowStatus.PENDING;
	}

	public static CampaignImportRow create(CampaignImport campaignImport, int rowNumber, String originalUrl, String externalId) {
		return new CampaignImportRow(campaignImport, rowNumber, originalUrl, externalId);
	}

	public void succeed(Long linkId) {
		this.status = ImportRowStatus.SUCCEEDED;
		this.linkId = linkId;
		this.processedAt = Instant.now();
	}

	public void fail(String errorCode, String errorMessage) {
		this.status = ImportRowStatus.FAILED;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.processedAt = Instant.now();
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
