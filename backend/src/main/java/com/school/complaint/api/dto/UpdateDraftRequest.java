package com.school.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDraftRequest(
		@NotBlank String draftText,
		String revisedBy
) {
}
