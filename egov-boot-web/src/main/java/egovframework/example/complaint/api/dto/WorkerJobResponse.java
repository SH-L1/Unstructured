package egovframework.example.complaint.api.dto;

import java.util.Map;
import java.util.UUID;

public record WorkerJobResponse(
		UUID id,
		UUID complaintId,
		String jobType,
		int attempts,
		int maxAttempts,
		long leaseSeconds,
		String inputHash,
		Map<String, Object> payload
) {
}
