package com.school.complaint.api.dto;

import com.school.complaint.domain.Complaint;
import com.school.complaint.domain.ComplaintStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ComplaintResponse(
		UUID id,
		String sourceChannel,
		String rawText,
		String locationText,
		ComplaintStatus status,
		LocalDateTime createdAt
) {
	public static ComplaintResponse from(Complaint complaint) {
		return new ComplaintResponse(
				complaint.getId(),
				complaint.getSourceChannel(),
				complaint.getRawText(),
				complaint.getLocationText(),
				complaint.getStatus(),
				complaint.getCreatedAt()
		);
	}
}
