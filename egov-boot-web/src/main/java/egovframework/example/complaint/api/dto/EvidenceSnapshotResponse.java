package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.EvidenceSnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EvidenceSnapshotResponse(
		UUID id,
		String sourceType,
		String sourceId,
		String title,
		String content,
		String sourceUrl,
		String legalBasis,
		String sourceVersion,
		String jurisdictionCode,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		String sourceStatus,
		String contentHash,
		boolean supportsClaim,
		LocalDateTime createdAt
) {
	public static EvidenceSnapshotResponse from(EvidenceSnapshot snapshot) {
		return new EvidenceSnapshotResponse(
				snapshot.getId(),
				snapshot.getSourceType(),
				snapshot.getSourceId(),
				snapshot.getTitle(),
				snapshot.getContent(),
				snapshot.getSourceUrl(),
				snapshot.getLegalBasis(),
				snapshot.getSourceVersion(),
				snapshot.getJurisdictionCode(),
				snapshot.getEffectiveFrom(),
				snapshot.getEffectiveTo(),
				snapshot.getSourceStatus(),
				snapshot.getContentHash(),
				snapshot.isSupportsClaim(),
				snapshot.getCreatedAt()
		);
	}
}
