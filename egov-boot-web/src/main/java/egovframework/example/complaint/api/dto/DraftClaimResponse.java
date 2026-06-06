package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.DraftClaim;
import java.util.Set;
import java.util.UUID;

public record DraftClaimResponse(
		UUID id,
		int claimIndex,
		String claimText,
		String claimType,
		Set<String> evidenceSourceIds
) {
	public static DraftClaimResponse from(DraftClaim claim) {
		return new DraftClaimResponse(
				claim.getId(),
				claim.getClaimIndex(),
				claim.getClaimText(),
				claim.getClaimType(),
				claim.sourceDocumentIds()
		);
	}
}
