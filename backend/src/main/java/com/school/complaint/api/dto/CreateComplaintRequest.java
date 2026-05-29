package com.school.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateComplaintRequest(
		String sourceChannel,
		String title,
		@NotBlank String rawText,
		String locationText
) {
}
