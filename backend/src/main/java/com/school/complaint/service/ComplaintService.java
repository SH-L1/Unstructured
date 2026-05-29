package com.school.complaint.service;

import com.school.complaint.api.dto.ComplaintAnalysisResponse;
import com.school.complaint.api.dto.ComplaintResponse;
import com.school.complaint.api.dto.CreateComplaintRequest;
import com.school.complaint.api.dto.DraftResponse;
import com.school.complaint.api.dto.RagContextResponse;
import com.school.complaint.api.dto.UpdateDraftRequest;
import com.school.complaint.domain.Complaint;
import com.school.complaint.domain.ComplaintAnalysis;
import com.school.complaint.domain.Department;
import com.school.complaint.domain.DraftRevision;
import com.school.complaint.domain.KnowledgeDocument;
import com.school.complaint.domain.OfficialDraft;
import com.school.complaint.domain.RagContext;
import com.school.complaint.domain.Sentiment;
import com.school.complaint.domain.SourceChannel;
import com.school.complaint.domain.Urgency;
import com.school.complaint.repository.ComplaintAnalysisRepository;
import com.school.complaint.repository.ComplaintRepository;
import com.school.complaint.repository.DepartmentRepository;
import com.school.complaint.repository.DraftRevisionRepository;
import com.school.complaint.repository.KnowledgeDocumentRepository;
import com.school.complaint.repository.OfficialDraftRepository;
import com.school.complaint.repository.RagContextRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplaintService {

	private static final String MOCK_MODEL_NAME = "mock-bedrock-illegal-dumping-v1";

	private final ComplaintRepository complaintRepository;
	private final ComplaintAnalysisRepository complaintAnalysisRepository;
	private final DepartmentRepository departmentRepository;
	private final KnowledgeDocumentRepository knowledgeDocumentRepository;
	private final OfficialDraftRepository officialDraftRepository;
	private final DraftRevisionRepository draftRevisionRepository;
	private final RagContextRepository ragContextRepository;

	public ComplaintService(
			ComplaintRepository complaintRepository,
			ComplaintAnalysisRepository complaintAnalysisRepository,
			DepartmentRepository departmentRepository,
			KnowledgeDocumentRepository knowledgeDocumentRepository,
			OfficialDraftRepository officialDraftRepository,
			DraftRevisionRepository draftRevisionRepository,
			RagContextRepository ragContextRepository) {
		this.complaintRepository = complaintRepository;
		this.complaintAnalysisRepository = complaintAnalysisRepository;
		this.departmentRepository = departmentRepository;
		this.knowledgeDocumentRepository = knowledgeDocumentRepository;
		this.officialDraftRepository = officialDraftRepository;
		this.draftRevisionRepository = draftRevisionRepository;
		this.ragContextRepository = ragContextRepository;
	}

	@Transactional
	public ComplaintResponse create(CreateComplaintRequest request) {
		SourceChannel sourceChannel = normalizeSourceChannel(request.sourceChannel());
		String title = normalizeTitle(request.title(), request.rawText());
		Complaint complaint = new Complaint(sourceChannel, title, request.rawText(), request.locationText());
		return ComplaintResponse.from(complaintRepository.save(complaint));
	}

	@Transactional(readOnly = true)
	public List<ComplaintResponse> findAll() {
		return complaintRepository.findAll().stream()
				.sorted(Comparator.comparing(Complaint::getCreatedAt).reversed())
				.map(ComplaintResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ComplaintResponse findById(UUID id) {
		return ComplaintResponse.from(getComplaint(id));
	}

	@Transactional
	public ComplaintAnalysisResponse analyze(UUID id) {
		return complaintAnalysisRepository.findByComplaintId(id)
				.map(this::toAnalysisResponse)
				.orElseGet(() -> createMockAnalysis(getComplaint(id)));
	}

	@Transactional
	public DraftResponse generateDraft(UUID id) {
		Complaint complaint = getComplaint(id);
		List<OfficialDraft> existingDrafts = officialDraftRepository.findByComplaintIdOrderByCreatedAtDesc(id);
		if (!existingDrafts.isEmpty()) {
			OfficialDraft latestDraft = existingDrafts.get(0);
			return toDraftResponse(latestDraft, ragContextRepository.findByOfficialDraftIdOrderByScoreDesc(latestDraft.getId()));
		}
		ComplaintAnalysis analysis = complaintAnalysisRepository.findByComplaintId(id)
				.orElseGet(() -> createMockAnalysisEntity(complaint));

		List<KnowledgeDocument> documents = searchKnowledgeDocuments(analysis);
		List<RagContext> detachedContexts = documents.stream()
				.map(document -> new RagContext(
						complaint,
						null,
						document,
						document.getLegalBasis(),
						snippet(document.getContent()),
						scoreFor(document)
				))
				.toList();

		String draftText = buildDraftText(complaint, analysis, detachedContexts);
		OfficialDraft draft = officialDraftRepository.save(new OfficialDraft(complaint, draftText, MOCK_MODEL_NAME));
		List<RagContext> savedContexts = detachedContexts.stream()
				.map(context -> new RagContext(
						complaint,
						draft,
						context.getKnowledgeDocument(),
						context.getLegalBasis(),
						context.getContentSnippet(),
						context.getScore()
				))
				.map(ragContextRepository::save)
				.toList();

		complaint.markDraftGenerated();
		return toDraftResponse(draft, savedContexts);
	}

	@Transactional(readOnly = true)
	public List<RagContextResponse> findRagContexts(UUID id) {
		getComplaint(id);
		return ragContextRepository.findByComplaintIdOrderByScoreDesc(id).stream()
				.map(this::toRagContextResponse)
				.toList();
	}

	@Transactional
	public String findGeoJson(UUID id) {
		getComplaint(id);
		return complaintAnalysisRepository.findByComplaintId(id)
				.map(ComplaintAnalysis::getGeoJson)
				.orElseGet(() -> analyze(id).geoJson());
	}

	@Transactional
	public DraftResponse reviseDraft(UUID id, UpdateDraftRequest request) {
		Complaint complaint = getComplaint(id);
		DraftResponse currentDraftResponse = generateDraft(id);
		OfficialDraft draft = officialDraftRepository.findById(currentDraftResponse.draftId())
				.orElseThrow(() -> new EntityNotFoundException("Draft not found: " + currentDraftResponse.draftId()));
		String beforeText = draft.getDraftText();
		draft.revise(request.draftText());
		draftRevisionRepository.save(new DraftRevision(
				draft,
				beforeText,
				request.draftText(),
				normalizeReviser(request.revisedBy())
		));
		complaint.markDraftGenerated();
		return toDraftResponse(draft, ragContextRepository.findByOfficialDraftIdOrderByScoreDesc(draft.getId()));
	}

	private ComplaintAnalysisResponse createMockAnalysis(Complaint complaint) {
		return toAnalysisResponse(createMockAnalysisEntity(complaint));
	}

	private ComplaintAnalysis createMockAnalysisEntity(Complaint complaint) {
		String text = (complaint.getTitle() + " " + complaint.getRawText()).toLowerCase(Locale.ROOT);
		boolean illegalDumping = containsAny(text, "투기", "쓰레기", "폐기물", "폐가구", "무단");
		Urgency urgency = containsAny(text, "위험", "악취", "긴급", "화재", "벌레") ? Urgency.HIGH : Urgency.NORMAL;
		Sentiment sentiment = containsAny(text, "화남", "짜증", "불안") ? Sentiment.ANGER
				: containsAny(text, "불편", "냄새", "악취", "벌레") ? Sentiment.DISCOMFORT : Sentiment.NEUTRAL;
		Department department = departmentRepository.findByCode(illegalDumping ? "RESOURCE_RECYCLING" : "CIVIL_AFFAIRS")
				.orElseThrow(() -> new EntityNotFoundException("Seed department not found"));
		String intent = illegalDumping ? "불법 투기 신고" : "일반 생활 민원";
		String locationText = complaint.getLocationText();
		String geoJson = locationText == null || locationText.isBlank() ? null : """
				{"type":"Feature","properties":{"complaintId":"%s","locationText":"%s","department":"%s","urgency":"%s"},"geometry":null}
				""".formatted(complaint.getId(), escapeJson(locationText), escapeJson(department.getName()), urgency.name()).trim();
		String analysisJson = """
				{"intent":"%s","urgency":"%s","sentiment":"%s","department":"%s","keywords":["불법 투기","폐기물","생활폐기물"],"requiredAction":"현장 확인 및 수거 조치"}
				""".formatted(intent, urgency.name(), sentiment.name(), department.getName()).trim();

		ComplaintAnalysis analysis = complaintAnalysisRepository.save(new ComplaintAnalysis(
				complaint,
				intent,
				urgency,
				sentiment,
				department,
				locationText,
				geoJson,
				analysisJson
		));
		complaint.markAnalyzed();
		return analysis;
	}

	private List<KnowledgeDocument> searchKnowledgeDocuments(ComplaintAnalysis analysis) {
		Map<Long, KnowledgeDocument> uniqueDocuments = new LinkedHashMap<>();
		for (String keyword : List.of("불법 투기", "폐기물", "생활폐기물", analysis.getIntent())) {
			for (KnowledgeDocument document : knowledgeDocumentRepository.searchByKeyword(keyword)) {
				uniqueDocuments.put(document.getId(), document);
			}
		}
		if (uniqueDocuments.isEmpty()) {
			return knowledgeDocumentRepository.findAll();
		}
		return uniqueDocuments.values().stream().limit(3).toList();
	}

	private String buildDraftText(Complaint complaint, ComplaintAnalysis analysis, List<RagContext> contexts) {
		String legalBasis = contexts.stream()
				.map(RagContext::getLegalBasis)
				.filter(value -> value != null && !value.isBlank())
				.findFirst()
				.orElse("관련 법령 및 민원 처리 기준");
		return """
				안녕하십니까. 귀하께서 접수하신 '%s' 민원은 %s 건으로 확인되었습니다.

				제출하신 내용과 위치 정보를 검토한 결과, 담당 부서는 %s로 분류됩니다. 해당 부서에서 현장 확인 후 생활폐기물 수거 및 필요한 행정 절차를 검토할 예정입니다.

				본 초안은 %s을 참고하여 작성되었으며, 최종 답변 전 담당자 검토를 거쳐 확정됩니다.
				""".formatted(
				complaint.getTitle(),
				analysis.getIntent(),
				analysis.getDepartment().getName(),
				legalBasis
		).trim();
	}

	private DraftResponse toDraftResponse(OfficialDraft draft, List<RagContext> references) {
		return new DraftResponse(
				draft.getId(),
				draft.getComplaint().getId(),
				draft.getDraftText(),
				draft.getModelName(),
				draft.getStatus().name(),
				references.stream().map(this::toRagContextResponse).toList()
		);
	}

	private ComplaintAnalysisResponse toAnalysisResponse(ComplaintAnalysis analysis) {
		return new ComplaintAnalysisResponse(
				analysis.getComplaint().getId(),
				analysis.getIntent(),
				analysis.getUrgency().name(),
				analysis.getSentiment().name(),
				analysis.getDepartment().getCode(),
				analysis.getDepartment().getName(),
				analysis.getLocationText(),
				analysis.getGeoJson(),
				analysis.getAnalysisJson()
		);
	}

	private RagContextResponse toRagContextResponse(RagContext context) {
		KnowledgeDocument document = context.getKnowledgeDocument();
		return new RagContextResponse(
				document.getId(),
				document.getTitle(),
				document.getDocumentType().name(),
				context.getLegalBasis(),
				context.getContentSnippet(),
				context.getScore()
		);
	}

	private Complaint getComplaint(UUID id) {
		return complaintRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + id));
	}

	private SourceChannel normalizeSourceChannel(String sourceChannel) {
		if (sourceChannel == null || sourceChannel.isBlank()) {
			return SourceChannel.WEB;
		}
		return SourceChannel.valueOf(sourceChannel.trim().toUpperCase(Locale.ROOT));
	}

	private String normalizeTitle(String title, String rawText) {
		if (title != null && !title.isBlank()) {
			return title.trim();
		}
		String normalized = rawText.trim();
		return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
	}

	private String normalizeReviser(String revisedBy) {
		if (revisedBy == null || revisedBy.isBlank()) {
			return "local-admin";
		}
		return revisedBy.trim();
	}

	private boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private String snippet(String content) {
		return content.length() <= 160 ? content : content.substring(0, 160);
	}

	private double scoreFor(KnowledgeDocument document) {
		return switch (document.getDocumentType()) {
			case LAW -> 0.94;
			case ORDINANCE -> 0.91;
			case MANUAL -> 0.88;
			case CASE -> 0.84;
			default -> 0.80;
		};
	}

	private String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
