package egovframework.example.complaint.api.dto;

import java.util.List;
import java.util.UUID;

public record DraftResponse(
		Long draftId,
		UUID complaintId,
		String draftText,
		String modelName,
		String status,
		List<RagContextResponse> references
) {
}
