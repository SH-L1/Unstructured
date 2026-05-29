package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateComplaintRequest(
		@Size(max = 50) String sourceChannel,
		@NotBlank @Size(max = 10000) String rawText,
		@Size(max = 500)
		String locationText
) {
}
