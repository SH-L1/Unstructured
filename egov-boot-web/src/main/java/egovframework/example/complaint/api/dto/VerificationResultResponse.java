package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.VerificationResult;
import java.time.LocalDateTime;

public record VerificationResultResponse(
		String ruleCode,
		String status,
		String message,
		boolean hardFailure,
		LocalDateTime createdAt
) {
	public static VerificationResultResponse from(VerificationResult result) {
		return new VerificationResultResponse(
				result.getRuleCode(),
				result.getStatus(),
				result.getMessage(),
				result.isHardFailure(),
				result.getCreatedAt()
		);
	}
}
