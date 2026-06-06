package egovframework.example.complaint.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WorkerResultRequest(
		@NotBlank @Size(max = 100) String workerId,
		@NotBlank @Size(max = 80) String provider,
		@NotBlank @Size(max = 100) String modelName,
		@NotBlank @Size(max = 80) String promptVersion,
		@NotBlank @Size(max = 80) String schemaVersion,
		@NotBlank @Size(min = 64, max = 128) String inputHash,
		@NotBlank @Size(min = 64, max = 128) String outputHash,
		@Min(0) @Max(2000) long costUnits,
		@Min(0) long durationMs,
		@Min(0) int retryCount,
		@NotNull JsonNode output,
		@Size(max = 50) List<Long> evidenceDocumentIds
) {
}
