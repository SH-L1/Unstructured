package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDraftRequest(
		@NotBlank
		@Size(max = 10000)
		String draftText
) {
}
