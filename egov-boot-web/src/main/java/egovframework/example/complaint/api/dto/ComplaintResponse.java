package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ComplaintResponse(
		UUID id,
		String sourceChannel,
		String rawText,
		String locationText,
		String urgency,
		String department,
		ComplaintStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static ComplaintResponse from(Complaint complaint) {
		return new ComplaintResponse(
				complaint.getId(),
				complaint.getSourceChannel(),
				complaint.getRawText(),
				complaint.getLocationText(),
				complaint.getUrgency(),
				complaint.getDepartment(),
				complaint.getStatus(),
				complaint.getCreatedAt(),
				complaint.getUpdatedAt()
		);
	}
}
