package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "draft_claims")
public class DraftClaim extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "official_draft_id", nullable = false)
	private OfficialDraft officialDraft;

	@Column(nullable = false)
	private int claimIndex;

	@Column(nullable = false, columnDefinition = "text")
	private String claimText;

	@Column(nullable = false, length = 40)
	private String claimType;

	@Column(nullable = false, columnDefinition = "text")
	private String sourceDocumentIds;

	protected DraftClaim() {
	}

	public DraftClaim(OfficialDraft officialDraft, int claimIndex, String claimText, String claimType,
			String sourceDocumentIds) {
		this.officialDraft = officialDraft;
		this.claimIndex = claimIndex;
		this.claimText = claimText;
		this.claimType = claimType;
		this.sourceDocumentIds = sourceDocumentIds;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public UUID getId() {
		return id;
	}

	public int getClaimIndex() {
		return claimIndex;
	}

	public String getClaimText() {
		return claimText;
	}

	public String getClaimType() {
		return claimType;
	}

	public Set<String> sourceDocumentIds() {
		if (sourceDocumentIds == null || sourceDocumentIds.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(sourceDocumentIds.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.collect(Collectors.toSet());
	}
}
