package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.ComplaintIssue;
import java.util.List;
import java.util.UUID;

public record ComplaintIssueResponse(
		UUID id,
		int issueIndex,
		String summary,
		String complaintType,
		String jurisdictionStatus,
		String safetyRisk,
		String expressionRisk,
		String processability,
		String status,
		List<String> departmentCandidates,
		List<String> locationCandidates
) {
	public static ComplaintIssueResponse from(
			ComplaintIssue issue,
			List<String> departmentCandidates,
			List<String> locationCandidates
	) {
		return new ComplaintIssueResponse(
				issue.getId(),
				issue.getIssueIndex(),
				issue.getSummary(),
				issue.getComplaintType().name(),
				issue.getJurisdictionStatus(),
				issue.getSafetyRisk(),
				issue.getExpressionRisk(),
				issue.getProcessability(),
				issue.getStatus(),
				departmentCandidates,
				locationCandidates
		);
	}
}
