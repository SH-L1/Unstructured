package egovframework.example.complaint.api.dto;

public record RagContextResponse(
		String documentId,
		String title,
		String documentType,
		String legalBasis,
		String contentSnippet,
		String purpose,
		String verificationStatus,
		String jurisdictionCode,
		String effectiveFrom,
		String effectiveTo,
		String sourceUrl
) {
}
