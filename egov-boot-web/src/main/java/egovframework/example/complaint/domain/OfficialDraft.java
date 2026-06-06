package egovframework.example.complaint.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "official_drafts")
public class OfficialDraft extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_id", nullable = false)
	private Complaint complaint;

	@Column(nullable = false, columnDefinition = "text")
	private String draftText;

	@Column(nullable = false, length = 100)
	private String modelName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DraftStatus status;

	@Column(length = 100)
	private String reviewedBy;

	@Column(length = 100)
	private String approvedBy;

	@Column(columnDefinition = "text")
	private String reviewNotes;

	@Version
	@Column(nullable = false)
	private long version;

	protected OfficialDraft() {
	}

	public OfficialDraft(Complaint complaint, String draftText, String modelName) {
		this.complaint = complaint;
		this.draftText = draftText;
		this.modelName = modelName;
		this.status = DraftStatus.DRAFT;
	}

	public void revise(String draftText) {
		this.draftText = draftText;
		this.status = DraftStatus.REVISED;
	}

	public void markReviewed(String actor, String notes) {
		if (status != DraftStatus.DRAFT && status != DraftStatus.REVISED) {
			throw new IllegalStateException("Draft is not ready for review");
		}
		this.reviewedBy = actor;
		this.reviewNotes = notes;
		this.status = DraftStatus.APPROVAL_PENDING;
	}

	public void approve(String actor, String notes) {
		if (status != DraftStatus.APPROVAL_PENDING) {
			throw new IllegalStateException("Draft is not awaiting approval");
		}
		requireSeparateApprover(actor);
		this.approvedBy = actor;
		this.reviewNotes = notes;
		this.status = DraftStatus.APPROVED;
	}

	public void rejectFromReview(String notes) {
		if (status != DraftStatus.DRAFT && status != DraftStatus.REVISED) {
			throw new IllegalStateException("Only a draft awaiting review can be rejected by a reviewer");
		}
		reject(notes);
	}

	public void rejectFromApproval(String actor, String notes) {
		if (status != DraftStatus.APPROVAL_PENDING) {
			throw new IllegalStateException("Only a draft awaiting approval can be rejected by an approver");
		}
		requireSeparateApprover(actor);
		reject(notes);
	}

	private void requireSeparateApprover(String actor) {
		if (actor.equals(reviewedBy)) {
			throw new IllegalStateException("Reviewer cannot make the approval-stage decision on the same draft");
		}
	}

	public void rejectForVerification(String notes) {
		if (status != DraftStatus.DRAFT && status != DraftStatus.REVISED) {
			throw new IllegalStateException("Only an unreviewed draft can be rejected by deterministic verification");
		}
		reject(notes);
	}

	private void reject(String notes) {
		this.reviewNotes = notes;
		this.status = DraftStatus.REJECTED;
	}

	public Long getId() {
		return id;
	}

	public Complaint getComplaint() {
		return complaint;
	}

	public String getDraftText() {
		return draftText;
	}

	public String getModelName() {
		return modelName;
	}

	public DraftStatus getStatus() {
		return status;
	}

	public String getReviewedBy() {
		return reviewedBy;
	}

	public String getApprovedBy() {
		return approvedBy;
	}

	public long getVersion() {
		return version;
	}
}
