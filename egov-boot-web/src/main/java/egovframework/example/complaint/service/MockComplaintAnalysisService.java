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
		String intent = containsAny(text, "trash", "waste", "dumping", "garbage", "recycle", "쓰레기", "폐기물", "무단투기", "불법투기", "폐가구")
				? "무단투기 및 생활폐기물 신고"
				: containsAny(text, "road", "street", "pothole", "broken", "도로", "포트홀", "파손", "보도")
						? "도로시설물 파손 신고"
						: containsAny(text, "parking", "불법주차", "불법주정차", "주정차", "주차")
								? "불법주정차 신고"
								: containsAny(text, "traffic sign", "sign", "교통표지", "표지판", "신호")
										? "교통시설물 정비 요청"
										: "일반 민원";
		String urgency = containsAny(text, "danger", "broken", "urgent", "accident", "risk", "위험", "긴급", "사고", "파손")
				? "HIGH"
				: "NORMAL";
		String sentiment = containsAny(text, "complaint", "uncomfortable", "angry", "damage", "unsafe", "불편", "불만", "위험", "피해")
				? "DISCOMFORT"
				: "NEUTRAL";
		String department = departmentRoutingService.route(intent);
		String locationText = complaint.getLocationText();
		String geoJson = locationText == null || locationText.isBlank() ? null : """
				{"type":"Feature","properties":{"locationText":"%s"},"geometry":null}
				""".formatted(escapeJson(locationText)).trim();

		return new ComplaintAnalysisResponse(
				complaint.getId(),
				intent,
				intent.contains("무단투기") ? "ILLEGAL_DUMPING"
						: intent.contains("도로") ? "ROAD_DAMAGE"
						: intent.contains("주정차") ? "ILLEGAL_PARKING"
						: intent.contains("교통") ? "TRAFFIC_SIGN"
						: "GENERAL",
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
