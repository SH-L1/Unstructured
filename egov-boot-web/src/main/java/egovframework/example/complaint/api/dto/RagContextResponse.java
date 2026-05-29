package egovframework.example.complaint.api.dto;

public record RagContextResponse(
		String documentId,
		String title,
		String documentType,
		String legalBasis,
		String contentSnippet,
		double score
) {
}
