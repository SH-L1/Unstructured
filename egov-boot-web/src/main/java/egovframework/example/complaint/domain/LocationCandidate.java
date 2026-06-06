package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "location_candidates")
public class LocationCandidate extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_issue_id", nullable = false)
	private ComplaintIssue issue;

	@Column(nullable = false, length = 500)
	private String locationText;

	@Column(nullable = false, length = 40)
	private String source;

	@Column(nullable = false, length = 40)
	private String status;

	@Column(length = 100)
	private String confirmedBy;

	private LocalDateTime confirmedAt;

	protected LocationCandidate() {
	}

	public LocationCandidate(ComplaintIssue issue, String locationText, String source) {
		this.issue = issue;
		this.locationText = locationText;
		this.source = source;
		this.status = "PENDING";
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public void confirm(String actor) {
		status = "CONFIRMED";
		confirmedBy = actor;
		confirmedAt = LocalDateTime.now();
	}

	public UUID getId() {
		return id;
	}

	public ComplaintIssue getIssue() {
		return issue;
	}

	public String getLocationText() {
		return locationText;
	}

	public String getStatus() {
		return status;
	}

	public String getSource() {
		return source;
	}
}
