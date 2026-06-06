package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.ProcessingJob;
import java.util.UUID;

public record ProcessingJobResponse(
		UUID id,
		UUID complaintId,
		String jobType,
		String status,
		int attempts,
		int maxAttempts,
		String failureReason,
		String resultReference,
		long version
) {
	public static ProcessingJobResponse from(ProcessingJob job) {
		return new ProcessingJobResponse(
				job.getId(),
				job.getComplaintId(),
				job.getJobType().name(),
				job.getStatus().name(),
				job.getAttempts(),
				job.getMaxAttempts(),
				job.getFailureReason(),
				job.getResultReference(),
				job.getVersion()
		);
	}
}
