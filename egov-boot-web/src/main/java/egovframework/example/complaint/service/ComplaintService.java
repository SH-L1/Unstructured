package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.AttachmentResponse;
import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.api.dto.DraftResponse;
import egovframework.example.complaint.api.dto.RagContextResponse;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.ComplaintAttachment;
import egovframework.example.complaint.domain.ComplaintStatus;
import egovframework.example.complaint.domain.ComplaintType;
import egovframework.example.complaint.domain.Department;
import egovframework.example.complaint.domain.DraftRevision;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.OfficialDraft;
import egovframework.example.complaint.domain.RagContext;
import egovframework.example.complaint.domain.Sentiment;
import egovframework.example.complaint.domain.SourceChannel;
import egovframework.example.complaint.domain.Urgency;
import egovframework.example.complaint.repository.ComplaintAnalysisRepository;
import egovframework.example.complaint.repository.ComplaintAttachmentRepository;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.DepartmentRepository;
import egovframework.example.complaint.repository.DraftRevisionRepository;
import egovframework.example.complaint.repository.OfficialDraftRepository;
import egovframework.example.complaint.repository.RagContextRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ComplaintService {

	private static final String MOCK_MODEL_NAME = "mock-korean-civil-complaint-v1";

	private final ComplaintRepository complaintRepository;
	private final ComplaintAttachmentRepository complaintAttachmentRepository;
	private final ComplaintAnalysisRepository complaintAnalysisRepository;
	private final DepartmentRepository departmentRepository;
	private final OfficialDraftRepository officialDraftRepository;
	private final DraftRevisionRepository draftRevisionRepository;
	private final RagContextRepository ragContextRepository;
	private final FileStorageService fileStorageService;
	private final KnowledgeDocumentSearchService knowledgeDocumentSearchService;
	private final ObjectProvider<ComplaintAnalysisClient> complaintAnalysisClientProvider;
	private final ObjectProvider<DraftGenerationClient> draftGenerationClientProvider;
	private final String aiProvider;
	private final String openAiModel;

	public ComplaintService(
			ComplaintRepository complaintRepository,
			ComplaintAttachmentRepository complaintAttachmentRepository,
			ComplaintAnalysisRepository complaintAnalysisRepository,
			DepartmentRepository departmentRepository,
			OfficialDraftRepository officialDraftRepository,
			DraftRevisionRepository draftRevisionRepository,
			RagContextRepository ragContextRepository,
			FileStorageService fileStorageService,
			KnowledgeDocumentSearchService knowledgeDocumentSearchService,
			ObjectProvider<ComplaintAnalysisClient> complaintAnalysisClientProvider,
			ObjectProvider<DraftGenerationClient> draftGenerationClientProvider,
			@Value("${app.ai.provider:mock-bedrock}") String aiProvider,
			@Value("${app.openai.model:gpt-4o-mini}") String openAiModel
	) {
		this.complaintRepository = complaintRepository;
		this.complaintAttachmentRepository = complaintAttachmentRepository;
		this.complaintAnalysisRepository = complaintAnalysisRepository;
		this.departmentRepository = departmentRepository;
		this.officialDraftRepository = officialDraftRepository;
		this.draftRevisionRepository = draftRevisionRepository;
		this.ragContextRepository = ragContextRepository;
		this.fileStorageService = fileStorageService;
		this.knowledgeDocumentSearchService = knowledgeDocumentSearchService;
		this.complaintAnalysisClientProvider = complaintAnalysisClientProvider;
		this.draftGenerationClientProvider = draftGenerationClientProvider;
		this.aiProvider = aiProvider;
		this.openAiModel = openAiModel;
	}

	@Transactional
	public ComplaintResponse create(CreateComplaintRequest request) {
		Complaint complaint = new Complaint(
				normalizeSourceChannel(request.sourceChannel()),
				request.rawText(),
				request.locationText()
		);
		return ComplaintResponse.from(complaintRepository.save(complaint));
	}

	@Transactional(readOnly = true)
	public Page<ComplaintResponse> findAll(ComplaintStatus status, String department, String urgency, Pageable pageable) {
		Page<Complaint> complaints = complaintRepository.findAll(filterByStatus(status), pageable);
		List<ComplaintResponse> responses = complaints.getContent().stream()
				.map(complaint -> ComplaintResponse.from(
						complaint,
						complaintAnalysisRepository.findByComplaintId(complaint.getId())
								.map(this::toAnalysisResponse)
								.orElse(null)
				))
				.filter(response -> !StringUtils.hasText(department) || department.equalsIgnoreCase(response.department()))
				.filter(response -> !StringUtils.hasText(urgency) || urgency.equalsIgnoreCase(response.urgency()))
				.toList();
		return new PageImpl<>(responses, pageable, complaints.getTotalElements());
	}

	@Transactional(readOnly = true)
	public ComplaintResponse findById(UUID id) {
		Complaint complaint = getComplaint(id);
		return ComplaintResponse.from(
				complaint,
				complaintAnalysisRepository.findByComplaintId(id).map(this::toAnalysisResponse).orElse(null)
		);
	}

	@Transactional
	public ComplaintResponse updateStatus(UUID id, ComplaintStatus status) {
		Complaint complaint = getComplaint(id);
		complaint.changeStatus(status);
		return findById(id);
	}

	@Transactional
	public ComplaintAnalysisResponse analyze(UUID id) {
		return complaintAnalysisRepository.findByComplaintId(id)
				.map(this::toAnalysisResponse)
				.orElseGet(() -> toAnalysisResponse(createAnalysis(getComplaint(id))));
	}

	@Transactional
	public DraftResponse generateDraft(UUID id) {
		Complaint complaint = getComplaint(id);
		List<OfficialDraft> existingDrafts = officialDraftRepository.findByComplaintIdOrderByCreatedAtDesc(id);
		if (!existingDrafts.isEmpty()) {
			OfficialDraft latest = existingDrafts.get(0);
			return toDraftResponse(latest, ragContextRepository.findByOfficialDraftIdOrderByScoreDesc(latest.getId()));
		}

		ComplaintAnalysis analysis = complaintAnalysisRepository.findByComplaintId(id)
				.orElseGet(() -> createAnalysis(complaint));
		List<KnowledgeDocument> documents = knowledgeDocumentSearchService.search(analysis);
		DraftGenerationClient draftClient = draftGenerationClientProvider.getIfAvailable();
		String draftText = draftClient == null
				? buildFallbackDraftText(complaint, analysis, documents)
				: draftClient.generateDraft(complaint, analysis, documents);
		OfficialDraft draft = officialDraftRepository.save(new OfficialDraft(complaint, draftText, modelName()));
		List<RagContext> contexts = documents.stream()
				.map(document -> new RagContext(
						complaint,
						draft,
						document,
						document.getLegalBasis(),
						snippet(document.getContent()),
						scoreFor(document)
				))
				.map(ragContextRepository::save)
				.toList();
		complaint.markDraftGenerated();
		return toDraftResponse(draft, contexts);
	}

	@Transactional
	public DraftResponse updateDraft(UUID id, String draftText) {
		Complaint complaint = getComplaint(id);
		DraftResponse current = generateDraft(id);
		OfficialDraft draft = officialDraftRepository.findById(current.draftId())
				.orElseThrow(() -> new EntityNotFoundException("Draft not found: " + current.draftId()));
		String beforeText = draft.getDraftText();
		draft.revise(draftText);
		draftRevisionRepository.save(new DraftRevision(draft, beforeText, draftText, "local-admin"));
		complaint.markDraftGenerated();
		return toDraftResponse(draft, ragContextRepository.findByOfficialDraftIdOrderByScoreDesc(draft.getId()));
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
		return analyze(id).geoJson();
	}

	@Transactional
	public AttachmentResponse addAttachment(UUID id, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Attachment file is required");
		}
		Complaint complaint = getComplaint(id);
		try {
			StoredFile storedFile = fileStorageService.store(
					file.getOriginalFilename(),
					file.getContentType(),
					file.getSize(),
					file.getInputStream()
			);
			ComplaintAttachment attachment = new ComplaintAttachment(
					complaint,
					storedFile.originalFilename(),
					storedFile.contentType(),
					storedFile.size(),
					storedFile.storageKey()
			);
			return AttachmentResponse.from(complaintAttachmentRepository.save(attachment));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read attachment file", exception);
		}
	}

	@Transactional(readOnly = true)
	public List<AttachmentResponse> findAttachments(UUID id) {
		getComplaint(id);
		return complaintAttachmentRepository.findByComplaint_IdOrderByCreatedAtDesc(id).stream()
				.map(AttachmentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public DownloadedAttachment downloadAttachment(UUID complaintId, UUID attachmentId) {
		getComplaint(complaintId);
		ComplaintAttachment attachment = complaintAttachmentRepository.findById(attachmentId)
				.filter(candidate -> candidate.getComplaintId().equals(complaintId))
				.orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
		StoredFileContent content = fileStorageService.load(attachment.getStorageKey());
		return new DownloadedAttachment(attachment.getOriginalFilename(), attachment.getContentType(), content.bytes());
	}

	@Transactional
	public void deleteAttachment(UUID complaintId, UUID attachmentId) {
		getComplaint(complaintId);
		ComplaintAttachment attachment = complaintAttachmentRepository.findById(attachmentId)
				.filter(candidate -> candidate.getComplaintId().equals(complaintId))
				.orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
		fileStorageService.delete(attachment.getStorageKey());
		complaintAttachmentRepository.delete(attachment);
	}

	public String attachmentDisposition(String filename) {
		String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
		return "attachment; filename*=UTF-8''" + encoded;
	}

	private ComplaintAnalysis createAnalysis(Complaint complaint) {
		ComplaintAnalysisClient analysisClient = complaintAnalysisClientProvider.getIfAvailable();
		if (analysisClient != null) {
			return createAnalysisFromResult(complaint, analysisClient.analyze(complaint));
		}
		return createRuleBasedAnalysis(complaint);
	}

	private ComplaintAnalysis createRuleBasedAnalysis(Complaint complaint) {
		String text = complaint.getRawText().toLowerCase(Locale.ROOT);
		ComplaintType complaintType = inferComplaintType(text);
		String intent = intentFor(complaintType);
		Urgency urgency = complaintType == ComplaintType.HAZARDOUS_MATERIAL
				? Urgency.EMERGENCY
				: containsAny(text, "danger", "broken", "urgent", "accident", "risk", "unsafe", "위험", "긴급", "사고")
						? Urgency.HIGH
						: Urgency.NORMAL;
		Sentiment sentiment = containsAny(text, "complaint", "uncomfortable", "angry", "damage", "unsafe", "불편", "불안", "위험")
				? Sentiment.DISCOMFORT
				: Sentiment.NEUTRAL;
		Department department = departmentRepository.findByCode(departmentCodeFor(complaintType))
				.orElseThrow(() -> new EntityNotFoundException("Department seed data is missing: " + departmentCodeFor(complaintType)));
		String geoJson = complaint.getLocationText() == null || complaint.getLocationText().isBlank() ? null : """
				{"type":"Feature","properties":{"complaintId":"%s","locationText":"%s","department":"%s","urgency":"%s"},"geometry":null}
				""".formatted(complaint.getId(), escapeJson(complaint.getLocationText()), escapeJson(department.getName()), urgency.name()).trim();
		String analysisJson = """
				{"intent":"%s","complaintType":"%s","urgency":"%s","sentiment":"%s","department":"%s","keywords":%s,"requiredAction":"Field verification and department routing"}
				""".formatted(intent, complaintType.name(), urgency.name(), sentiment.name(), department.getName(), keywordsFor(complaintType)).trim();
		ComplaintAnalysis analysis = complaintAnalysisRepository.save(new ComplaintAnalysis(
				complaint,
				intent,
				complaintType,
				urgency,
				sentiment,
				department,
				complaint.getLocationText(),
				geoJson,
				analysisJson
		));
		complaint.markAnalyzed();
		return analysis;
	}

	private ComplaintAnalysis createAnalysisFromResult(Complaint complaint, ComplaintAnalysisResult result) {
		Department department = departmentRepository.findByCode(result.departmentCode())
				.orElseThrow(() -> new EntityNotFoundException("Department not found: " + result.departmentCode()));
		ComplaintAnalysis analysis = complaintAnalysisRepository.save(new ComplaintAnalysis(
				complaint,
				result.intent(),
				inferComplaintType(result.intent() + " " + result.analysisJson()),
				Urgency.valueOf(result.urgency().trim().toUpperCase(Locale.ROOT)),
				Sentiment.valueOf(result.sentiment().trim().toUpperCase(Locale.ROOT)),
				department,
				result.locationText(),
				result.geoJson(),
				result.analysisJson()
		));
		complaint.markAnalyzed();
		return analysis;
	}

	private String buildFallbackDraftText(Complaint complaint, ComplaintAnalysis analysis, List<KnowledgeDocument> documents) {
		if (documents.isEmpty()) {
			return """
					안녕하십니까. 접수하신 민원은 %s 건으로 확인했습니다.
					현재 민원 내용과 직접적으로 일치하는 내부 참고문서가 확인되지 않아, 관련 없는 법령을 근거로 답변하지 않습니다.
					%s에서 현장 확인 및 관계기관 협의 필요성을 우선 검토하겠습니다.
					""".formatted(analysis.getIntent(), analysis.getDepartment().getName()).trim();
		}
		String legalBasis = documents.stream()
				.map(KnowledgeDocument::getLegalBasis)
				.filter(StringUtils::hasText)
				.findFirst()
				.orElse("관련 민원 처리 기준");
		return """
				안녕하십니까. 접수하신 민원은 %s 건으로 확인했습니다.
				해당 민원은 %s에서 검토할 예정이며, 제출하신 위치와 내용을 바탕으로 현장 확인 필요 여부를 판단하겠습니다.
				검토 과정에서는 %s 등 관련 근거를 참고하되, 본 문안은 담당자 검토용 초안입니다.
				""".formatted(analysis.getIntent(), analysis.getDepartment().getName(), legalBasis).trim();
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
				analysis.getComplaintType().name(),
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
				String.valueOf(document.getId()),
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

	private Specification<Complaint> filterByStatus(ComplaintStatus status) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(criteriaBuilder.equal(root.get("status"), status));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private SourceChannel normalizeSourceChannel(String sourceChannel) {
		if (sourceChannel == null || sourceChannel.isBlank()) {
			return SourceChannel.WEB;
		}
		String normalized = sourceChannel.trim().toUpperCase(Locale.ROOT).replace("-", "_");
		try {
			return SourceChannel.valueOf(normalized);
		}
		catch (IllegalArgumentException exception) {
			return SourceChannel.WEB;
		}
	}

	private ComplaintType inferComplaintType(String text) {
		if (text == null) {
			return ComplaintType.GENERAL;
		}
		String value = text.toLowerCase(Locale.ROOT);
		if (containsAny(value, "biohazard", "biochemical", "hazardous", "chemical", "bomb", "explosive",
				"생화학", "위험물", "화학물질", "유해물질", "폭탄", "폭발물", "재난", "테러")) {
			return ComplaintType.HAZARDOUS_MATERIAL;
		}
		if (containsAny(value, "dumping", "waste", "garbage", "trash", "쓰레기", "폐기물", "무단투기")) {
			return ComplaintType.ILLEGAL_DUMPING;
		}
		if (containsAny(value, "road", "pothole", "sidewalk", "도로", "포트홀", "보도", "파손")) {
			return ComplaintType.ROAD_DAMAGE;
		}
		if (containsAny(value, "parking", "불법주정차", "주차")) {
			return ComplaintType.ILLEGAL_PARKING;
		}
		if (containsAny(value, "traffic sign", "signal", "sign", "교통표지", "신호")) {
			return ComplaintType.TRAFFIC_SIGN;
		}
		if (containsAny(value, "noise", "소음")) {
			return ComplaintType.NOISE;
		}
		if (containsAny(value, "environment", "pollution", "환경", "오염", "악취")) {
			return ComplaintType.ENVIRONMENT;
		}
		return ComplaintType.GENERAL;
	}

	private String intentFor(ComplaintType complaintType) {
		return switch (complaintType) {
			case ILLEGAL_DUMPING -> "무단투기 및 생활폐기물 신고";
			case ROAD_DAMAGE -> "도로시설물 파손 신고";
			case ILLEGAL_PARKING -> "불법주정차 신고";
			case TRAFFIC_SIGN -> "교통시설물 정비 요청";
			case NOISE -> "소음 민원";
			case ENVIRONMENT -> "환경 생활불편 민원";
			case HAZARDOUS_MATERIAL -> "생화학 위험물 및 폭발물 의심 긴급 신고";
			default -> "일반 민원";
		};
	}

	private String departmentCodeFor(ComplaintType complaintType) {
		return switch (complaintType) {
			case ILLEGAL_DUMPING, ENVIRONMENT -> "RESOURCE_RECYCLING";
			case ROAD_DAMAGE -> "ROAD";
			case ILLEGAL_PARKING, TRAFFIC_SIGN -> "TRAFFIC";
			case HAZARDOUS_MATERIAL -> "SAFETY_CONTROL";
			default -> "CIVIL_AFFAIRS";
		};
	}

	private String keywordsFor(ComplaintType complaintType) {
		return switch (complaintType) {
			case ILLEGAL_DUMPING -> "[\"쓰레기\",\"폐기물\",\"무단투기\",\"현장확인\"]";
			case ROAD_DAMAGE -> "[\"도로\",\"포트홀\",\"파손\",\"보수\",\"현장점검\"]";
			case ILLEGAL_PARKING -> "[\"불법주정차\",\"주차\",\"단속\",\"교통\"]";
			case TRAFFIC_SIGN -> "[\"교통표지\",\"신호\",\"정비\"]";
			case NOISE -> "[\"소음\",\"생활불편\",\"현장확인\"]";
			case ENVIRONMENT -> "[\"환경\",\"오염\",\"생활불편\",\"현장확인\"]";
			case HAZARDOUS_MATERIAL -> "[\"생화학\",\"위험물\",\"폭탄\",\"폭발물\",\"화학물질\",\"유해물질\",\"재난\",\"경찰\",\"소방\"]";
			default -> "[\"민원\",\"접수\",\"담당부서\"]";
		};
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
		return content.length() <= 180 ? content : content.substring(0, 180);
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

	private String modelName() {
		if ("openai".equalsIgnoreCase(aiProvider)) {
			return "openai:" + openAiModel;
		}
		return MOCK_MODEL_NAME;
	}

	private String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public record DownloadedAttachment(String originalFilename, String contentType, byte[] bytes) {
	}
}
