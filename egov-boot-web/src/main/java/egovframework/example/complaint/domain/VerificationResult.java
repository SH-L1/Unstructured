package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_results")
public class VerificationResult {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false)
	private UUID complaintId;

	private Long officialDraftId;

	@Column(nullable = false, length = 80)
	private String ruleCode;

	@Column(nullable = false, length = 40)
	private String status;

	@Column(nullable = false, columnDefinition = "text")
	private String message;

	@Column(nullable = false)
	private boolean hardFailure;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected VerificationResult() {
	}

	public VerificationResult(UUID complaintId, Long officialDraftId, String ruleCode, String status, String message,
			boolean hardFailure) {
		this.complaintId = complaintId;
		this.officialDraftId = officialDraftId;
		this.ruleCode = ruleCode;
		this.status = status;
		this.message = message;
		this.hardFailure = hardFailure;
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

	public String getRuleCode() {
		return ruleCode;
	}

	public String getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

	public boolean isHardFailure() {
		return hardFailure;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
