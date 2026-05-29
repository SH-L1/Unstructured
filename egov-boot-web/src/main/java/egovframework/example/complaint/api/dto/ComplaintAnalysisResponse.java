package egovframework.example.complaint.api.dto;

import java.util.UUID;

public record ComplaintAnalysisResponse(
		UUID complaintId,
		String intent,
		String complaintType,
		String urgency,
		String sentiment,
		String departmentCode,
		String department,
		String locationText,
		String geoJson,
		String analysisJson
) {
}
