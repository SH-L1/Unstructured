package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateComplaintRequest(
		String sourceChannel,
		@NotBlank String rawText,
		String locationText
) {
}
