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

	private static final String MOCK_MODEL_NAME = "mock-bedrock-illegal-dumping-v1";

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
			ObjectProvider<DraftGenerationClient> draftGenerationClientProvider
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
	}

	@Transactional
	public ComplaintResponse create(CreateComplaintRequest request) {
		SourceChannel sourceChannel = normalizeSourceChannel(request.sourceChannel());
		Complaint complaint = new Complaint(sourceChannel, request.rawText(), request.locationText());
		return ComplaintResponse.from(complaintRepository.save(complaint));
	}

	@Transactional(readOnly = true)
	public Page<ComplaintResponse> findAll(
			ComplaintStatus status,
			String department,
			String urgency,
			Pageable pageable
	) {
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
				.orElseGet(() -> toAnalysisResponse(createMockAnalysis(getComplaint(id))));
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
				.orElseGet(() -> createMockAnalysis(complaint));
		List<KnowledgeDocument> documents = knowledgeDocumentSearchService.search(analysis);
		String draftText = draftGenerationClientProvider.getIfAvailable() == null
				? buildDraftText(complaint, analysis, documents)
				: draftGenerationClientProvider.getObject().generateDraft(complaint, analysis, documents);
		OfficialDraft draft = officialDraftRepository.save(new OfficialDraft(complaint, draftText, MOCK_MODEL_NAME));
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
		return new DownloadedAttachment(
				attachment.getOriginalFilename(),
				attachment.getContentType(),
				content.bytes()
		);
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

	private ComplaintAnalysis createMockAnalysis(Complaint complaint) {
		ComplaintAnalysisClient analysisClient = complaintAnalysisClientProvider.getIfAvailable();
		if (analysisClient != null) {
			return createAnalysisFromResult(complaint, analysisClient.analyze(complaint));
		}
		String text = complaint.getRawText().toLowerCase(Locale.ROOT);
		boolean waste = containsAny(text, "trash", "waste", "dumping", "garbage", "recycle");
		boolean road = containsAny(text, "road", "street", "pothole", "broken");
		boolean trafficSign = containsAny(text, "traffic sign", "no parking sign", "sign");
		ComplaintType complaintType = waste ? ComplaintType.ILLEGAL_DUMPING
				: road ? ComplaintType.ROAD_DAMAGE
				: trafficSign ? ComplaintType.TRAFFIC_SIGN
				: ComplaintType.GENERAL;
		String intent = switch (complaintType) {
			case ILLEGAL_DUMPING -> "Waste dumping report";
			case ROAD_DAMAGE -> "Road facility complaint";
			case TRAFFIC_SIGN -> "Traffic sign complaint";
			default -> "General civil complaint";
		};
		Urgency urgency = containsAny(text, "danger", "broken", "urgent", "accident", "risk", "insects", "smell")
				? Urgency.HIGH : Urgency.NORMAL;
		Sentiment sentiment = containsAny(text, "complaint", "uncomfortable", "angry", "damage", "unsafe", "smell")
				? Sentiment.DISCOMFORT : Sentiment.NEUTRAL;
		Department department = departmentRepository.findByCode(waste ? "RESOURCE_RECYCLING" : road ? "ROAD" : "CIVIL_AFFAIRS")
				.orElseThrow(() -> new EntityNotFoundException("Department seed data is missing"));
		String geoJson = complaint.getLocationText() == null || complaint.getLocationText().isBlank() ? null : """
				{"type":"Feature","properties":{"complaintId":"%s","locationText":"%s","department":"%s","urgency":"%s"},"geometry":null}
				""".formatted(complaint.getId(), escapeJson(complaint.getLocationText()), escapeJson(department.getName()), urgency.name()).trim();
		String analysisJson = """
				{"intent":"%s","urgency":"%s","sentiment":"%s","department":"%s","keywords":["waste","dumping","civil complaint"],"requiredAction":"site inspection and removal review"}
				""".formatted(intent, urgency.name(), sentiment.name(), department.getName()).trim();
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
				inferComplaintType(result.intent()),
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

	private String buildDraftText(Complaint complaint, ComplaintAnalysis analysis, List<KnowledgeDocument> documents) {
		String legalBasis = documents.stream()
				.map(KnowledgeDocument::getLegalBasis)
				.filter(StringUtils::hasText)
				.findFirst()
				.orElse("relevant civil complaint handling standards");
		return """
				Hello. We have received your complaint and classified it as: %s.
				The responsible department is expected to be %s. The department will review the submitted details, inspect the site when needed, and take follow-up action according to the relevant procedure.
				This draft references %s and should be reviewed by the responsible officer before final response.
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

	private ComplaintType inferComplaintType(String intent) {
		if (intent == null) {
			return ComplaintType.GENERAL;
		}
		String text = intent.toLowerCase(Locale.ROOT);
		if (containsAny(text, "dumping", "waste", "garbage", "trash")) {
			return ComplaintType.ILLEGAL_DUMPING;
		}
		if (containsAny(text, "road", "pothole", "sidewalk")) {
			return ComplaintType.ROAD_DAMAGE;
		}
		if (containsAny(text, "parking")) {
			return ComplaintType.ILLEGAL_PARKING;
		}
		if (containsAny(text, "traffic sign", "sign")) {
			return ComplaintType.TRAFFIC_SIGN;
		}
		if (containsAny(text, "noise")) {
			return ComplaintType.NOISE;
		}
		if (containsAny(text, "environment", "pollution")) {
			return ComplaintType.ENVIRONMENT;
		}
		return ComplaintType.GENERAL;
	}

	private String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public record DownloadedAttachment(
			String originalFilename,
			String contentType,
			byte[] bytes
	) {
	}
}
