package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.OfficialDraft;
import java.util.UUID;

public record TrustDraftResponse(
		Long id,
		UUID complaintId,
		String draftText,
		String modelName,
		String status,
		String reviewedBy,
		String approvedBy,
		long version
) {
	public static TrustDraftResponse from(OfficialDraft draft) {
		return new TrustDraftResponse(
				draft.getId(),
				draft.getComplaint().getId(),
				draft.getDraftText(),
				draft.getModelName(),
				draft.getStatus().name(),
				draft.getReviewedBy(),
				draft.getApprovedBy(),
				draft.getVersion()
		);
	}
}
