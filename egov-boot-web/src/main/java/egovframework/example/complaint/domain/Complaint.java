package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "complaints")
public class Complaint extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 60, unique = true)
	private String receiptNumber;

	@Column(length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private SourceChannel sourceChannel;

	@Column(nullable = false, columnDefinition = "text")
	private String rawText;

	@Column(nullable = false, columnDefinition = "text")
	private String redactedText;

	@Column(length = 500)
	private String locationText;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ComplaintStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 40)
	private WorkflowBlocker workflowBlocker;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(nullable = false)
	private int attachmentRevision;

	protected Complaint() {
	}

	public Complaint(SourceChannel sourceChannel, String rawText, String redactedText, String locationText) {
		this.sourceChannel = sourceChannel;
		this.rawText = rawText;
		this.redactedText = redactedText;
		this.locationText = locationText;
		this.status = ComplaintStatus.RECEIVED;
		this.title = buildTitle(rawText);
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (status == null) {
			status = ComplaintStatus.RECEIVED;
		}
		if (receiptNumber == null) {
			String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
			receiptNumber = "CIV-" + date + "-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
		}
	}

	public UUID getId() {
		return id;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	public String getTitle() {
		return title;
	}

	public SourceChannel getSourceChannel() {
		return sourceChannel;
	}

	public String getRawText() {
		return rawText;
	}

	public String getRedactedText() {
		return redactedText;
	}

	public String getLocationText() {
		return locationText;
	}

	public ComplaintStatus getStatus() {
		return status;
	}

	public WorkflowBlocker getWorkflowBlocker() {
		return workflowBlocker;
	}

	public long getVersion() {
		return version;
	}

	public void markTriageReview() {
		transitionTo(ComplaintStatus.TRIAGE_REVIEW, ComplaintStatus.RECEIVED, ComplaintStatus.TRIAGE_REVIEW);
	}

	public void markDraftReview() {
		transitionTo(ComplaintStatus.DRAFT_REVIEW, ComplaintStatus.TRIAGE_REVIEW, ComplaintStatus.DRAFT_REVIEW);
	}

	public void markApprovalPending() {
		transitionTo(ComplaintStatus.APPROVAL_PENDING, ComplaintStatus.DRAFT_REVIEW);
	}

	public void markApproved() {
		transitionTo(ComplaintStatus.APPROVED, ComplaintStatus.APPROVAL_PENDING);
	}

	public void markCompleted() {
		transitionTo(ComplaintStatus.COMPLETED, ComplaintStatus.APPROVED);
	}

	public void returnToDraftReview() {
		transitionTo(ComplaintStatus.DRAFT_REVIEW, ComplaintStatus.APPROVAL_PENDING, ComplaintStatus.DRAFT_REVIEW);
	}

	public void block(WorkflowBlocker blocker) {
		this.workflowBlocker = blocker;
	}

	public void clearBlocker() {
		this.workflowBlocker = null;
	}

	public void confirmLocation(String locationText) {
		if (locationText == null || locationText.isBlank()) {
			throw new IllegalArgumentException("Confirmed location is required");
		}
		this.locationText = locationText;
		if (workflowBlocker == WorkflowBlocker.NEEDS_LOCATION) {
			workflowBlocker = null;
		}
	}

	public void recordAttachmentChange() {
		attachmentRevision++;
	}

	private void transitionTo(ComplaintStatus target, ComplaintStatus... allowedSources) {
		for (ComplaintStatus allowedSource : allowedSources) {
			if (status == allowedSource) {
				status = target;
				workflowBlocker = null;
				return;
			}
		}
		throw new IllegalStateException("Invalid complaint transition: " + status + " -> " + target);
	}

	private String buildTitle(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return null;
		}
		String normalized = rawText.replaceAll("\\s+", " ").trim();
		return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
	}
}
