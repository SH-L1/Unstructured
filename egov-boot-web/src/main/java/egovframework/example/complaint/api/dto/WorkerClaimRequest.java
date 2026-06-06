package egovframework.example.complaint.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WorkerClaimRequest(
		@NotBlank @Size(max = 100) String workerId,
		@Size(max = 10) List<String> jobTypes
) {
}
