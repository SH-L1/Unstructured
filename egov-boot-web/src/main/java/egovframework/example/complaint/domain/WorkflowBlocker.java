package egovframework.example.complaint.domain;

public enum WorkflowBlocker {
	NEEDS_LOCATION,
	NEEDS_JURISDICTION,
	EVIDENCE_INSUFFICIENT,
	CONFLICT_DETECTED,
	PROCESSING_FAILED
}
