package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.ComplaintAttachment;
import java.time.LocalDateTime;
import java.util.UUID;

public record AttachmentResponse(
		UUID id,
		UUID complaintId,
		String originalFilename,
		String contentType,
		long size,
		String storageKey,
		LocalDateTime createdAt
) {
	public static AttachmentResponse from(ComplaintAttachment attachment) {
		return new AttachmentResponse(
				attachment.getId(),
				attachment.getComplaintId(),
				attachment.getOriginalFilename(),
				attachment.getContentType(),
				attachment.getSize(),
				attachment.getStorageKey(),
				attachment.getCreatedAt()
		);
	}
}
