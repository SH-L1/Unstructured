package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentConfirmationRequest(
		@NotBlank
		@Size(max = 80)
		String departmentCode
) {
}
