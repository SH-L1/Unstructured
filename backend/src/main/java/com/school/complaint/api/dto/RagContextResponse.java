package com.school.complaint.api.dto;

public record RagContextResponse(
		String documentId,
		String legalBasis,
		String contentSnippet,
		double score
) {
}
