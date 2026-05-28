package egovframework.example.complaint.api.dto;

public record RagContextResponse(
		String documentId,
		String legalBasis,
		String contentSnippet,
		double score
) {
}
