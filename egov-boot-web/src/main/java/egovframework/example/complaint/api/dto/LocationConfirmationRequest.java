package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationConfirmationRequest(
		@NotBlank @Size(max = 500) String locationText
) {
}
