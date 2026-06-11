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

	@Column(nullable = false)
	private int recommendationScore;

	@Column(nullable = false, length = 60)
	private String recommendationSource;

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
		this.recommendationScore = inferScore(recommendationReason);
		this.recommendationSource = inferSource(recommendationReason);
		this.status = "CANDIDATE";
	}

	public DepartmentTask(
			ComplaintIssue issue,
			String departmentCode,
			String recommendationReason,
			int recommendationScore,
			String recommendationSource
	) {
		this.issue = issue;
		this.departmentCode = departmentCode;
		this.recommendationReason = recommendationReason;
		this.recommendationScore = Math.max(0, Math.min(100, recommendationScore));
		this.recommendationSource = recommendationSource;
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

	public int getRecommendationScore() {
		return recommendationScore;
	}

	public String getRecommendationSource() {
		return recommendationSource;
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

	private static int inferScore(String reason) {
		if (reason == null) {
			return 0;
		}
		int marker = reason.indexOf("score=");
		if (marker >= 0) {
			int start = marker + "score=".length();
			int end = start;
			while (end < reason.length() && Character.isDigit(reason.charAt(end))) {
				end++;
			}
			if (end > start) {
				try {
					return Math.max(0, Math.min(100, Integer.parseInt(reason.substring(start, end))));
				}
				catch (NumberFormatException ignored) {
					return 0;
				}
			}
		}
		if (reason.startsWith("RULE_BASED:")) {
			return 100;
		}
		if (reason.startsWith("FALLBACK:")) {
			return 70;
		}
		if (reason.startsWith("AI_MODEL:")) {
			return 60;
		}
		return 0;
	}

	private static String inferSource(String reason) {
		if (reason == null) {
			return "UNSPECIFIED";
		}
		if (reason.startsWith("RULE_BASED:")) {
			return "ASSIGNMENT_RULE";
		}
		if (reason.startsWith("FALLBACK:")) {
			return "DEFAULT_ROUTING";
		}
		if (reason.startsWith("AI_MODEL:")) {
			return "AI_MODEL";
		}
		return "UNSPECIFIED";
	}
}
