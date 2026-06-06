package egovframework.example.complaint.domain;

public enum ComplaintStatus {
	RECEIVED,
	TRIAGE_REVIEW,
	DRAFT_REVIEW,
	APPROVAL_PENDING,
	APPROVED,
	COMPLETED,
	REJECTED,
	// Legacy values remain readable during the V6 migration.
	ANALYZED,
	DRAFT_GENERATED,
	IN_PROGRESS
}
