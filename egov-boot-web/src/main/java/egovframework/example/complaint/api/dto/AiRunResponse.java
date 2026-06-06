package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.AiRun;
import java.time.LocalDateTime;

public record AiRunResponse(
		String taskType,
		String provider,
		String modelName,
		String promptVersion,
		String schemaVersion,
		String inputHash,
		String outputHash,
		String status,
		long costUnits,
		long durationMs,
		int retryCount,
		String failureReason,
		LocalDateTime createdAt
) {
	public static AiRunResponse from(AiRun run) {
		return new AiRunResponse(
				run.getTaskType(),
				run.getProvider(),
				run.getModelName(),
				run.getPromptVersion(),
				run.getSchemaVersion(),
				run.getInputHash(),
				run.getOutputHash(),
				run.getStatus(),
				run.getCostUnits(),
				run.getDurationMs(),
				run.getRetryCount(),
				run.getFailureReason(),
				run.getCreatedAt()
		);
	}
}
