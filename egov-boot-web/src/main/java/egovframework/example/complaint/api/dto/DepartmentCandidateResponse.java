package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.DepartmentTask;

public record DepartmentCandidateResponse(
		String code,
		String status,
		String recommendationReason,
		String confirmedBy,
		Integer score,
		String source,
		boolean selected,
		boolean verified
) {

	public static DepartmentCandidateResponse from(DepartmentTask task) {
		String status = task.getStatus();
		return new DepartmentCandidateResponse(
				task.getDepartmentCode(),
				status,
				task.getRecommendationReason(),
				task.getConfirmedBy(),
				task.getRecommendationScore(),
				task.getRecommendationSource(),
				"HUMAN_SELECTED".equals(status) || "VERIFIED".equals(status),
				"VERIFIED".equals(status)
		);
	}
}
