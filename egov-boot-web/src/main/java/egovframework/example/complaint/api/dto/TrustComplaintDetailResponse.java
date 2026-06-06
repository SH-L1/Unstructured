package egovframework.example.complaint.api.dto;

import java.util.List;

public record TrustComplaintDetailResponse(
		ComplaintResponse complaint,
		ComplaintAnalysisResponse analysis,
		List<ComplaintIssueResponse> issues,
		TrustDraftResponse draft,
		List<DraftClaimResponse> draftClaims,
		List<EvidenceSnapshotResponse> evidence,
		List<VerificationResultResponse> verificationResults,
		List<AiRunResponse> aiRuns,
		List<HumanReviewResponse> humanReviews
) {
}
