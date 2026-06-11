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
				resolveSourceUrl(snapshot.getSourceUrl()),
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

	private static String resolveSourceUrl(String sourceUrl) {
		if (sourceUrl == null) {
			return null;
		}
		String normalized = sourceUrl.replace("\\", "/");
		int dataIdx = normalized.indexOf("ai-rag-engine/data/");
		if (dataIdx != -1) {
			String subPath = normalized.substring(dataIdx + "ai-rag-engine/data/".length());
			String baseUrl = "http://localhost:8081";
			try {
				if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() != null) {
					baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
				}
			} catch (Exception e) {
				// Fallback to default
			}
			return baseUrl + "/data/" + subPath;
		}
		return sourceUrl;
	}
}
