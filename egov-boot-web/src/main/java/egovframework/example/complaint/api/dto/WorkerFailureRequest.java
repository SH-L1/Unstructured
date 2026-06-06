package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkerFailureRequest(
		@NotBlank @Size(max = 100) String workerId,
		@NotBlank @Size(max = 2000) String reason,
		boolean retryable
) {
}
