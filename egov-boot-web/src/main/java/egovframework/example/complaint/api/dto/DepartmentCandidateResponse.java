package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.DepartmentTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DepartmentCandidateResponse(
		String code,
		String status,
		String recommendationReason,
		String confirmedBy,
		Integer score,
		boolean selected,
		boolean verified
) {

	private static final Pattern SCORE = Pattern.compile("score=([0-9]+)");

	public static DepartmentCandidateResponse from(DepartmentTask task) {
		String status = task.getStatus();
		return new DepartmentCandidateResponse(
				task.getDepartmentCode(),
				status,
				task.getRecommendationReason(),
				task.getConfirmedBy(),
				score(task.getRecommendationReason()),
				"HUMAN_SELECTED".equals(status) || "VERIFIED".equals(status),
				"VERIFIED".equals(status)
		);
	}

	private static Integer score(String reason) {
		if (reason == null) {
			return null;
		}
		Matcher matcher = SCORE.matcher(reason);
		return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
	}
}
