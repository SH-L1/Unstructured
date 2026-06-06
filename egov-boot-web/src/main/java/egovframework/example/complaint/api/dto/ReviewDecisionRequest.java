package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(
		@NotNull Boolean approved,
		@Size(max = 5000) String notes
) {
}
