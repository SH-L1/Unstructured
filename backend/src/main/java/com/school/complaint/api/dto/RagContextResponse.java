package com.school.complaint.api.dto;

public record RagContextResponse(
		Long documentId,
		String title,
		String documentType,
		String legalBasis,
		String contentSnippet,
		double score
) {
}
