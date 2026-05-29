package egovframework.example.complaint.service;

public record ComplaintAnalysisResult(
		String intent,
		String urgency,
		String sentiment,
		String departmentCode,
		String locationText,
		String geoJson,
		String analysisJson
) {
}
