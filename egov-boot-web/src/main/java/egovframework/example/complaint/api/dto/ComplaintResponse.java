package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ComplaintResponse(
		UUID id,
		String receiptNumber,
		String title,
		String sourceChannel,
		String redactedText,
		String locationText,
		String urgency,
		String department,
		ComplaintStatus status,
		String workflowBlocker,
		long version,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static ComplaintResponse from(Complaint complaint) {
		return new ComplaintResponse(
				complaint.getId(),
				complaint.getReceiptNumber(),
				complaint.getTitle(),
				complaint.getSourceChannel().name(),
				complaint.getRedactedText(),
				complaint.getLocationText(),
				null,
				null,
				complaint.getStatus(),
				complaint.getWorkflowBlocker() == null ? null : complaint.getWorkflowBlocker().name(),
				complaint.getVersion(),
				complaint.getCreatedAt(),
				complaint.getUpdatedAt()
		);
	}

	public static ComplaintResponse from(Complaint complaint, ComplaintAnalysisResponse analysis) {
		return new ComplaintResponse(
				complaint.getId(),
				complaint.getReceiptNumber(),
				complaint.getTitle(),
				complaint.getSourceChannel().name(),
				complaint.getRedactedText(),
				complaint.getLocationText(),
				analysis == null ? null : analysis.urgency(),
				analysis == null ? null : analysis.department(),
				complaint.getStatus(),
				complaint.getWorkflowBlocker() == null ? null : complaint.getWorkflowBlocker().name(),
				complaint.getVersion(),
				complaint.getCreatedAt(),
				complaint.getUpdatedAt()
		);
	}
}
