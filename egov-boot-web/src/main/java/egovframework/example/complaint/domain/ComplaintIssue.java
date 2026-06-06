package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "complaint_issues")
public class ComplaintIssue extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_id", nullable = false)
	private Complaint complaint;

	@Column(nullable = false)
	private int issueIndex;

	@Column(nullable = false, length = 500)
	private String summary;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private ComplaintType complaintType;

	@Column(nullable = false, length = 40)
	private String jurisdictionStatus;

	@Column(nullable = false, length = 40)
	private String safetyRisk;

	@Column(nullable = false, length = 40)
	private String expressionRisk;

	@Column(nullable = false, length = 40)
	private String processability;

	@Column(nullable = false, length = 40)
	private String status;

	@Version
	@Column(nullable = false)
	private long version;

	protected ComplaintIssue() {
	}

	public ComplaintIssue(Complaint complaint, int issueIndex, String summary, ComplaintType complaintType,
			String jurisdictionStatus, String safetyRisk, String expressionRisk, String processability) {
		this.complaint = complaint;
		this.issueIndex = issueIndex;
		this.summary = summary;
		this.complaintType = complaintType;
		this.jurisdictionStatus = jurisdictionStatus;
		this.safetyRisk = safetyRisk;
		this.expressionRisk = expressionRisk;
		this.processability = processability;
		this.status = "REVIEW_REQUIRED";
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

	public UUID getComplaintId() {
		return complaint.getId();
	}

	public int getIssueIndex() {
		return issueIndex;
	}

	public String getSummary() {
		return summary;
	}

	public ComplaintType getComplaintType() {
		return complaintType;
	}

	public String getJurisdictionStatus() {
		return jurisdictionStatus;
	}

	public String getSafetyRisk() {
		return safetyRisk;
	}

	public String getExpressionRisk() {
		return expressionRisk;
	}

	public String getProcessability() {
		return processability;
	}

	public String getStatus() {
		return status;
	}
}
