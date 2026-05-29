package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.domain.Complaint;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MockComplaintAnalysisService implements ComplaintAnalysisService {

	private final DepartmentRoutingService departmentRoutingService;

	public MockComplaintAnalysisService(DepartmentRoutingService departmentRoutingService) {
		this.departmentRoutingService = departmentRoutingService;
	}

	@Override
	public ComplaintAnalysisResponse analyze(Complaint complaint) {
		String text = complaint.getRawText().toLowerCase(Locale.ROOT);
		String intent = containsAny(text, "trash", "waste", "dumping", "garbage", "recycle")
				? "Waste dumping report"
				: containsAny(text, "road", "street", "pothole", "broken")
						? "Road facility complaint"
						: "General civil complaint";
		String urgency = containsAny(text, "danger", "broken", "urgent", "accident", "risk")
				? "HIGH"
				: "NORMAL";
		String sentiment = containsAny(text, "complaint", "uncomfortable", "angry", "damage", "unsafe")
				? "NEGATIVE"
				: "NEUTRAL";
		String department = departmentRoutingService.route(intent);
		String locationText = complaint.getLocationText();
		String geoJson = locationText == null || locationText.isBlank() ? null : """
				{"type":"Feature","properties":{"locationText":"%s"},"geometry":null}
				""".formatted(escapeJson(locationText)).trim();

		return new ComplaintAnalysisResponse(
				complaint.getId(),
				intent,
				intent.contains("Waste") ? "ILLEGAL_DUMPING" : intent.contains("Road") ? "ROAD_DAMAGE" : "GENERAL",
				urgency,
				sentiment,
				department,
				department,
				locationText,
				geoJson,
				"{}"
		);
	}

	private boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
