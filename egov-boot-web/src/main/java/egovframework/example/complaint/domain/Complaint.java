package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "complaints")
public class Complaint {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 50)
	private String sourceChannel;

	@Column(nullable = false, columnDefinition = "text")
	private String rawText;

	@Column(length = 500)
	private String locationText;

	@Column(length = 100)
	private String intent;

	@Column(length = 30)
	private String urgency;

	@Column(length = 30)
	private String sentiment;

	@Column(length = 100)
	private String department;

	@Column(columnDefinition = "text")
	private String draftText;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ComplaintStatus status;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column
	private LocalDateTime updatedAt;

	protected Complaint() {
	}

	public Complaint(String sourceChannel, String rawText, String locationText) {
		this.sourceChannel = sourceChannel;
		this.rawText = rawText;
		this.locationText = locationText;
		this.status = ComplaintStatus.RECEIVED;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (updatedAt == null) {
			updatedAt = createdAt;
		}
		if (status == null) {
			status = ComplaintStatus.RECEIVED;
		}
	}

	@jakarta.persistence.PreUpdate
	void preUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public UUID getId() {
		return id;
	}

	public String getSourceChannel() {
		return sourceChannel;
	}

	public String getRawText() {
		return rawText;
	}

	public String getLocationText() {
		return locationText;
	}

	public ComplaintStatus getStatus() {
		return status;
	}

	public String getIntent() {
		return intent;
	}

	public String getUrgency() {
		return urgency;
	}

	public String getSentiment() {
		return sentiment;
	}

	public String getDepartment() {
		return department;
	}

	public String getDraftText() {
		return draftText;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void applyAnalysis(String intent, String urgency, String sentiment, String department) {
		this.intent = intent;
		this.urgency = urgency;
		this.sentiment = sentiment;
		this.department = department;
		if (status == ComplaintStatus.RECEIVED) {
			this.status = ComplaintStatus.ANALYZED;
		}
	}

	public void updateDraft(String draftText) {
		this.draftText = draftText;
		if (status == ComplaintStatus.RECEIVED || status == ComplaintStatus.ANALYZED) {
			this.status = ComplaintStatus.DRAFTED;
		}
	}

	public void changeStatus(ComplaintStatus status) {
		this.status = status;
	}
}
