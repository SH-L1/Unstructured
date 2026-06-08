package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "department_tasks")
public class DepartmentTask extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_issue_id", nullable = false)
	private ComplaintIssue issue;

	@Column(nullable = false, length = 80)
	private String departmentCode;

	@Column(nullable = false, columnDefinition = "text")
	private String recommendationReason;

	@Column(nullable = false, length = 40)
	private String status;

	@Column(length = 100)
	private String confirmedBy;

	@Version
	@Column(nullable = false)
	private long version;

	protected DepartmentTask() {
	}

	public DepartmentTask(ComplaintIssue issue, String departmentCode, String recommendationReason) {
		this.issue = issue;
		this.departmentCode = departmentCode;
		this.recommendationReason = recommendationReason;
		this.status = "CANDIDATE";
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public String getDepartmentCode() {
		return departmentCode;
	}

	public String getRecommendationReason() {
		return recommendationReason;
	}

	public String getStatus() {
		return status;
	}

	public String getConfirmedBy() {
		return confirmedBy;
	}

	public long getVersion() {
		return version;
	}

	public void markCandidate() {
		this.status = "CANDIDATE";
		this.confirmedBy = null;
	}

	public void select(String actorName) {
		this.status = "HUMAN_SELECTED";
		this.confirmedBy = actorName;
	}

	public void verify(String actorName) {
		this.status = "VERIFIED";
		this.confirmedBy = actorName;
	}

	public void reject(String actorName) {
		this.status = "REJECTED";
		this.confirmedBy = actorName;
	}
}
