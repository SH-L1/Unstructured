package egovframework.example.complaint.api.dto;

import java.util.List;
import java.util.UUID;

public record DraftResponse(
		UUID complaintId,
		String draftText,
		List<RagContextResponse> references
) {
}
