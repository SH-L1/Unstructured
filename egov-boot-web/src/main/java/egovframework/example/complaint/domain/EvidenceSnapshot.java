package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "evidence_snapshots")
public class EvidenceSnapshot {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false)
	private UUID complaintId;

	private UUID retrievalRunId;

	@Column(nullable = false, length = 40)
	private String sourceType;

	@Column(nullable = false, length = 200)
	private String sourceId;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(length = 500)
	private String sourceUrl;

	@Column(length = 500)
	private String legalBasis;

	@Column(length = 200)
	private String sourceVersion;

	@Column(length = 80)
	private String jurisdictionCode;

	private LocalDate effectiveFrom;

	private LocalDate effectiveTo;

	@Column(nullable = false, length = 40)
	private String sourceStatus;

	@Column(nullable = false, length = 128)
	private String contentHash;

	@Column(nullable = false)
	private boolean supportsClaim;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected EvidenceSnapshot() {
	}

	public EvidenceSnapshot(UUID complaintId, UUID retrievalRunId, KnowledgeDocument document, String contentHash,
			boolean supportsClaim) {
		this.complaintId = complaintId;
		this.retrievalRunId = retrievalRunId;
		this.sourceType = document.getPurpose().name();
		this.sourceId = String.valueOf(document.getId());
		this.title = document.getTitle();
		this.content = document.getContent();
		this.sourceUrl = document.getSourceUrl();
		this.legalBasis = document.getLegalBasis();
		this.sourceVersion = document.getSourceVersion();
		this.jurisdictionCode = document.getJurisdictionCode();
		this.effectiveFrom = document.getEffectiveFrom();
		this.effectiveTo = document.getEffectiveTo();
		this.sourceStatus = document.getVerificationStatus().name();
		this.contentHash = contentHash;
		this.supportsClaim = supportsClaim;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public String getSourceType() {
		return sourceType;
	}

	public String getSourceId() {
		return sourceId;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public String getLegalBasis() {
		return legalBasis;
	}

	public String getSourceVersion() {
		return sourceVersion;
	}

	public String getJurisdictionCode() {
		return jurisdictionCode;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public LocalDate getEffectiveTo() {
		return effectiveTo;
	}

	public String getSourceStatus() {
		return sourceStatus;
	}

	public String getContentHash() {
		return contentHash;
	}

	public boolean isSupportsClaim() {
		return supportsClaim;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
