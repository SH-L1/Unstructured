package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkerSupportResultRequest(
		@NotBlank @Size(max = 100) String workerId,
		@NotBlank @Size(min = 64, max = 128) String inputHash,
		@NotBlank @Size(max = 200) String resultReference
) {
}
