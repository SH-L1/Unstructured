package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.api.dto.DraftResponse;
import egovframework.example.complaint.api.dto.RagContextResponse;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.repository.ComplaintRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplaintService {

	private final ComplaintRepository complaintRepository;

	public ComplaintService(ComplaintRepository complaintRepository) {
		this.complaintRepository = complaintRepository;
	}

	@Transactional
	public ComplaintResponse create(CreateComplaintRequest request) {
		String sourceChannel = normalizeSourceChannel(request.sourceChannel());
		Complaint complaint = new Complaint(sourceChannel, request.rawText(), request.locationText());
		return ComplaintResponse.from(complaintRepository.save(complaint));
	}

	@Transactional(readOnly = true)
	public List<ComplaintResponse> findAll() {
		return complaintRepository.findAll().stream()
				.map(ComplaintResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ComplaintResponse findById(UUID id) {
		return ComplaintResponse.from(getComplaint(id));
	}

	@Transactional(readOnly = true)
	public ComplaintAnalysisResponse analyze(UUID id) {
		Complaint complaint = getComplaint(id);
		String text = complaint.getRawText().toLowerCase(Locale.ROOT);
		String intent = containsAny(text, "쓰레기", "폐기물", "무단투기", "불법 투기", "trash", "waste", "dumping")
				? "불법 투기 신고"
				: "일반 생활 민원";
		String urgency = containsAny(text, "위험", "파손", "긴급", "악취", "danger", "broken", "urgent")
				? "HIGH"
				: "NORMAL";
		String sentiment = containsAny(text, "불편", "화남", "짜증", "민원", "complaint", "uncomfortable")
				? "NEGATIVE"
				: "NEUTRAL";
		String department = "불법 투기 신고".equals(intent) ? "자원순환과" : "민원처리과";
		String locationText = complaint.getLocationText();
		String geoJson = locationText == null || locationText.isBlank() ? null : """
				{"type":"Feature","properties":{"locationText":"%s"},"geometry":null}
				""".formatted(escapeJson(locationText)).trim();

		return new ComplaintAnalysisResponse(
				complaint.getId(),
				intent,
				urgency,
				sentiment,
				department,
				locationText,
				geoJson
		);
	}

	@Transactional(readOnly = true)
	public DraftResponse generateDraft(UUID id) {
		Complaint complaint = getComplaint(id);
		ComplaintAnalysisResponse analysis = analyze(id);
		List<RagContextResponse> references = List.of(
				new RagContextResponse(
						"mock-waste-ordinance-001",
						"폐기물관리법 및 지자체 폐기물 관리 조례",
						"생활폐기물 무단투기 신고 접수 시 현장 확인, 수거 조치, 위반 행위 확인 절차를 진행한다.",
						0.92
				),
				new RagContextResponse(
						"mock-civil-manual-001",
						"민원 응대 매뉴얼",
						"민원 답변은 접수 사실, 처리 예정 절차, 담당 부서, 추가 확인 사항을 포함하여 작성한다.",
						0.87
				)
		);
		String draftText = """
				안녕하십니까. 접수하신 민원은 %s 건으로 확인했습니다.
				제출하신 내용은 해당 부서인 %s에서 검토할 예정이며, 현장 확인이 필요한 경우 관련 절차에 따라 조치하겠습니다.
				검토 과정에서 추가 확인이 필요한 사항이 있을 경우 별도로 안내드리겠습니다.
				""".formatted(analysis.intent(), analysis.department()).trim();

		return new DraftResponse(complaint.getId(), draftText, references);
	}

	private Complaint getComplaint(UUID id) {
		return complaintRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + id));
	}

	private String normalizeSourceChannel(String sourceChannel) {
		if (sourceChannel == null || sourceChannel.isBlank()) {
			return "WEB";
		}
		return sourceChannel.trim().toUpperCase(Locale.ROOT);
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
