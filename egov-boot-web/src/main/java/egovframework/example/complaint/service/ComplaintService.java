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
import egovframework.example.complaint.domain.ComplaintSensitivePayload;
import egovframework.example.complaint.domain.AttachmentAnalysis;
import egovframework.example.complaint.domain.ComplaintStatus;
import egovframework.example.complaint.domain.ComplaintType;
import egovframework.example.complaint.domain.Department;
import egovframework.example.complaint.domain.DraftClaim;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.OfficialDraft;
import egovframework.example.complaint.domain.RagContext;
import egovframework.example.complaint.domain.Sentiment;
import egovframework.example.complaint.domain.SourceChannel;
import egovframework.example.complaint.domain.Urgency;
import egovframework.example.complaint.domain.VerificationResult;
import egovframework.example.complaint.repository.ComplaintAnalysisRepository;
import egovframework.example.complaint.repository.ComplaintAttachmentRepository;
import egovframework.example.complaint.repository.AttachmentAnalysisRepository;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.ComplaintSensitivePayloadRepository;
import egovframework.example.complaint.repository.DepartmentRepository;
import egovframework.example.complaint.repository.DraftClaimRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import egovframework.example.complaint.repository.OfficialDraftRepository;
import egovframework.example.complaint.repository.RagContextRepository;
import egovframework.example.complaint.repository.VerificationResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ComplaintService {

	private static final String MOCK_MODEL_NAME = "gpt-4o-mini";

	private final ComplaintRepository complaintRepository;
	private final ComplaintAttachmentRepository complaintAttachmentRepository;
	private final ComplaintAnalysisRepository complaintAnalysisRepository;
	private final DepartmentRepository departmentRepository;
	private final OfficialDraftRepository officialDraftRepository;
	private final DraftClaimRepository draftClaimRepository;
	private final RagContextRepository ragContextRepository;
	private final KnowledgeDocumentRepository knowledgeDocumentRepository;
	private final FileStorageService fileStorageService;
	private final KnowledgeDocumentSearchService knowledgeDocumentSearchService;
	private final ObjectProvider<DraftGenerationClient> draftGenerationClientProvider;
	private final RedactionService redactionService;
	private final AttachmentSecurityService attachmentSecurityService;
	private final AttachmentAnalysisRepository attachmentAnalysisRepository;
	private final ComplaintSensitivePayloadRepository complaintSensitivePayloadRepository;
	private final SensitivePayloadStorageService sensitivePayloadStorageService;
	private final AnalysisSchemaValidator analysisSchemaValidator;
	private final DraftSchemaValidator draftSchemaValidator;
	private final VerificationResultRepository verificationResultRepository;
	private final ContentHashService contentHashService;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;
	private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	public ComplaintService(
			ComplaintRepository complaintRepository,
			ComplaintAttachmentRepository complaintAttachmentRepository,
			ComplaintAnalysisRepository complaintAnalysisRepository,
			DepartmentRepository departmentRepository,
			OfficialDraftRepository officialDraftRepository,
			DraftClaimRepository draftClaimRepository,
			RagContextRepository ragContextRepository,
			KnowledgeDocumentRepository knowledgeDocumentRepository,
			FileStorageService fileStorageService,
			KnowledgeDocumentSearchService knowledgeDocumentSearchService,
			ObjectProvider<DraftGenerationClient> draftGenerationClientProvider,
			RedactionService redactionService,
			AttachmentSecurityService attachmentSecurityService,
			AttachmentAnalysisRepository attachmentAnalysisRepository,
			ComplaintSensitivePayloadRepository complaintSensitivePayloadRepository,
			SensitivePayloadStorageService sensitivePayloadStorageService,
			AnalysisSchemaValidator analysisSchemaValidator,
			DraftSchemaValidator draftSchemaValidator,
			VerificationResultRepository verificationResultRepository,
			ContentHashService contentHashService,
			ObjectMapper objectMapper,
			TransactionTemplate transactionTemplate,
			org.springframework.jdbc.core.JdbcTemplate jdbcTemplate
	) {
		this.complaintRepository = complaintRepository;
		this.complaintAttachmentRepository = complaintAttachmentRepository;
		this.complaintAnalysisRepository = complaintAnalysisRepository;
		this.departmentRepository = departmentRepository;
		this.officialDraftRepository = officialDraftRepository;
		this.draftClaimRepository = draftClaimRepository;
		this.ragContextRepository = ragContextRepository;
		this.knowledgeDocumentRepository = knowledgeDocumentRepository;
		this.fileStorageService = fileStorageService;
		this.knowledgeDocumentSearchService = knowledgeDocumentSearchService;
		this.draftGenerationClientProvider = draftGenerationClientProvider;
		this.redactionService = redactionService;
		this.attachmentSecurityService = attachmentSecurityService;
		this.attachmentAnalysisRepository = attachmentAnalysisRepository;
		this.complaintSensitivePayloadRepository = complaintSensitivePayloadRepository;
		this.sensitivePayloadStorageService = sensitivePayloadStorageService;
		this.analysisSchemaValidator = analysisSchemaValidator;
		this.draftSchemaValidator = draftSchemaValidator;
		this.verificationResultRepository = verificationResultRepository;
		this.contentHashService = contentHashService;
		this.objectMapper = objectMapper;
		this.transactionTemplate = transactionTemplate;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public ComplaintResponse create(CreateComplaintRequest request) {
		RedactionService.RedactionResult textRedaction = redactionService.inspect(request.rawText());
		RedactionService.RedactionResult locationRedaction = redactionService.inspect(request.locationText());
		Complaint complaint = new Complaint(
				normalizeSourceChannel(request.sourceChannel()),
				request.rawText(),
				textRedaction.redactedText(),
				locationRedaction.redactedText()
		);
		Complaint saved = complaintRepository.saveAndFlush(complaint);
		String rawStorageReference = null;
		try {
			rawStorageReference = sensitivePayloadStorageService.storeComplaintPayload(
					saved.getId(), request.rawText(), request.locationText()
			);
			complaintSensitivePayloadRepository.saveAndFlush(new ComplaintSensitivePayload(
					saved,
					rawStorageReference,
					textRedaction.redactedText(),
					"{\"rawText\":" + textRedaction.findingsJson()
							+ ",\"locationText\":" + locationRedaction.findingsJson() + "}"
			));
			return ComplaintResponse.from(saved);
		}
		catch (RuntimeException exception) {
			if (rawStorageReference != null) {
				try {
					sensitivePayloadStorageService.delete(rawStorageReference);
				}
				catch (RuntimeException cleanupException) {
					exception.addSuppressed(cleanupException);
				}
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public Page<ComplaintResponse> findAll(ComplaintStatus status, String department, String urgency, Pageable pageable) {
		Page<Complaint> complaints = complaintRepository.findAll(filterByStatusAndDepartmentAndUrgency(status, department, urgency), pageable);
		List<ComplaintResponse> responses = complaints.getContent().stream()
				.map(complaint -> ComplaintResponse.from(
						complaint,
						complaint.getAnalysis() != null ? toAnalysisResponse(complaint.getAnalysis()) : null
				))
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

	public ComplaintAnalysisResponse analyze(UUID id) {
		return complaintAnalysisRepository.findByComplaintId(id)
				.map(this::toAnalysisResponse)
				.orElseGet(() -> toAnalysisResponse(createAnalysis(getComplaint(id))));
	}

	public ComplaintAnalysisResponse applyWorkerAnalysis(UUID id, com.fasterxml.jackson.databind.JsonNode output) {
		Complaint complaint = getComplaint(id);
		if (complaintAnalysisRepository.findByComplaintId(id).isPresent()) {
			return toAnalysisResponse(complaintAnalysisRepository.findByComplaintId(id).orElseThrow());
		}
		ComplaintAnalysisResult result = new ComplaintAnalysisResult(
				output.path("intent").asText(),
				output.path("urgency").asText(),
				output.path("sentiment").asText(),
				output.path("departmentCode").asText(),
				output.path("locationText").isNull() ? null : output.path("locationText").asText(),
				null,
				output.toString()
		);
		return transactionTemplate.execute(status -> toAnalysisResponse(createAnalysisFromResult(getComplaint(complaint.getId()), result)));
	}

	public DraftResponse generateDraft(UUID id, Consumer<List<KnowledgeDocument>> retrievalObserver) {
		Complaint complaint = getComplaint(id);
		List<OfficialDraft> existingDrafts = officialDraftRepository.findByComplaintIdOrderByCreatedAtDesc(id);
		if (!existingDrafts.isEmpty()) {
			OfficialDraft latest = existingDrafts.get(0);
			if (latest.getStatus() != egovframework.example.complaint.domain.DraftStatus.REJECTED) {
				return toDraftResponse(latest, ragContextRepository.findByOfficialDraftIdOrderByIdAsc(latest.getId()));
			}
		}

		ComplaintAnalysis analysis = complaintAnalysisRepository.findByComplaintId(id)
				.orElseGet(() -> createAnalysis(complaint));

		boolean hasRegulatoryIssue = jdbcTemplate.queryForObject(
				"select count(*) from complaint_issues where complaint_id = ? and complaint_type in ('ILLEGAL_PARKING', 'ILLEGAL_DUMPING', 'HAZARDOUS_MATERIAL')",
				Integer.class,
				complaint.getId()
		) > 0;

		boolean requiresOfficialLaw = hasRegulatoryIssue;

		List<KnowledgeDocument> retrievedDocuments = knowledgeDocumentSearchService.search(analysis);
		retrievalObserver.accept(List.copyOf(retrievedDocuments));
		List<KnowledgeDocument> documents = retrievedDocuments.stream()
				.filter(document -> {
					if (requiresOfficialLaw) {
						return document.isOfficialLegalEvidence(LocalDate.now());
					} else {
						return document.isOfficialLegalEvidence(LocalDate.now())
								|| document.getPurpose() == egovframework.example.complaint.domain.KnowledgePurpose.LOCAL_ORDINANCE_REFERENCE
								|| document.getPurpose() == egovframework.example.complaint.domain.KnowledgePurpose.PROCEDURE;
					}
				})
				.filter(document -> {
					if (requiresOfficialLaw) {
						return hasRequiredOfficialMetadata(document);
					} else {
						return StringUtils.hasText(document.getContentHash())
								&& document.getContentHash().equals(contentHashService.sha256(document.getContent()));
					}
				})
				.toList();

		if (requiresOfficialLaw && documents.stream().noneMatch(document -> document.isOfficialLegalEvidence(LocalDate.now()))) {
			blockForVerification(
					complaint.getId(),
					egovframework.example.complaint.domain.WorkflowBlocker.EVIDENCE_INSUFFICIENT,
					"OFFICIAL_EVIDENCE_REQUIRED",
					"Verified official national-law evidence with required source metadata is required"
			);
			throw new IllegalStateException("Verified official evidence is required before a draft can be generated");
		}
		if (requiresOfficialLaw && hasOverlappingOfficialConflicts(documents)) {
			blockForVerification(
					complaint.getId(),
					egovframework.example.complaint.domain.WorkflowBlocker.CONFLICT_DETECTED,
					"CONFLICT_SCAN",
					"Overlapping official sources for the same legal basis contain conflicting content"
			);
			throw new IllegalStateException("Conflicting official evidence must be resolved before draft generation");
		}
		DraftGenerationClient draftClient = draftGenerationClientProvider.getIfAvailable();
		if (draftClient == null) {
			throw new IllegalStateException("Draft provider is unavailable");
		}
		DraftSchemaValidator.ValidatedDraft draft = draftSchemaValidator.validate(
				draftClient.generateDraft(complaint, analysis, documents, approvedAttachmentTexts(complaint.getId())),
				documents
		);
		if (redactionService.containsSensitivePattern(draft.renderedText())) {
			blockForVerification(
					complaint.getId(),
					egovframework.example.complaint.domain.WorkflowBlocker.PROCESSING_FAILED,
					"PII_OUTPUT_CHECK",
					"Draft output contained recognizable PII and was not stored"
			);
			throw new IllegalStateException("Draft schema validation failed: output contained recognizable PII");
		}
		return transactionTemplate.execute(status -> persistGeneratedDraft(complaint.getId(), draft, documents));
	}

	public DraftResponse applyWorkerDraft(
			UUID id,
			com.fasterxml.jackson.databind.JsonNode output,
			List<Long> evidenceDocumentIds,
			String workerModelName,
			Consumer<List<KnowledgeDocument>> retrievalObserver
	) {
		Complaint complaint = getComplaint(id);
		if (evidenceDocumentIds == null || evidenceDocumentIds.isEmpty() || evidenceDocumentIds.size() > 50) {
			throw new IllegalStateException("Worker draft must select governed evidence document IDs");
		}
		List<KnowledgeDocument> retrievedDocuments = knowledgeDocumentRepository.findAllById(
				new java.util.LinkedHashSet<>(evidenceDocumentIds)
		);
		if (retrievedDocuments.size() != new java.util.HashSet<>(evidenceDocumentIds).size()) {
			throw new IllegalStateException("Worker draft referenced unknown governed evidence");
		}
		retrievalObserver.accept(List.copyOf(retrievedDocuments));

		boolean hasRegulatoryIssue = jdbcTemplate.queryForObject(
				"select count(*) from complaint_issues where complaint_id = ? and complaint_type in ('ILLEGAL_PARKING', 'ILLEGAL_DUMPING', 'HAZARDOUS_MATERIAL')",
				Integer.class,
				complaint.getId()
		) > 0;

		boolean requiresOfficialLaw = hasRegulatoryIssue;

		List<KnowledgeDocument> documents = retrievedDocuments.stream()
				.filter(document -> {
					if (requiresOfficialLaw) {
						return document.isOfficialLegalEvidence(LocalDate.now());
					} else {
						return document.isOfficialLegalEvidence(LocalDate.now())
								|| document.getPurpose() == egovframework.example.complaint.domain.KnowledgePurpose.LOCAL_ORDINANCE_REFERENCE
								|| document.getPurpose() == egovframework.example.complaint.domain.KnowledgePurpose.PROCEDURE;
					}
				})
				.filter(document -> {
					if (requiresOfficialLaw) {
						return hasRequiredOfficialMetadata(document);
					} else {
						return StringUtils.hasText(document.getContentHash())
								&& document.getContentHash().equals(contentHashService.sha256(document.getContent()));
					}
				})
				.toList();

		if (requiresOfficialLaw && (documents.size() != retrievedDocuments.size() || documents.isEmpty())) {
			blockForVerification(
					complaint.getId(),
					egovframework.example.complaint.domain.WorkflowBlocker.EVIDENCE_INSUFFICIENT,
					"OFFICIAL_EVIDENCE_REQUIRED",
					"Worker-selected evidence must be complete verified official national-law evidence"
			);
			throw new IllegalStateException("Verified official evidence is required before a worker draft can be applied");
		}
		if (requiresOfficialLaw && hasOverlappingOfficialConflicts(documents)) {
			blockForVerification(
					complaint.getId(),
					egovframework.example.complaint.domain.WorkflowBlocker.CONFLICT_DETECTED,
					"CONFLICT_SCAN",
					"Worker-selected official sources contain conflicting content"
			);
			throw new IllegalStateException("Conflicting official evidence must be resolved before worker draft application");
		}
		DraftSchemaValidator.ValidatedDraft draft = draftSchemaValidator.validate(output.toString(), documents);
		if (redactionService.containsSensitivePattern(draft.renderedText())) {
			blockForVerification(
					complaint.getId(),
					egovframework.example.complaint.domain.WorkflowBlocker.PROCESSING_FAILED,
					"PII_OUTPUT_CHECK",
					"Worker draft output contained recognizable PII and was not stored"
			);
			throw new IllegalStateException("Draft schema validation failed: output contained recognizable PII");
		}
		return transactionTemplate.execute(status -> persistGeneratedDraft(
				complaint.getId(),
				draft,
				documents,
				workerModelName
		));
	}

	private DraftResponse persistGeneratedDraft(
			UUID complaintId,
			DraftSchemaValidator.ValidatedDraft generatedDraft,
			List<KnowledgeDocument> documents
	) {
		return persistGeneratedDraft(complaintId, generatedDraft, documents, modelName());
	}

	private DraftResponse persistGeneratedDraft(
			UUID complaintId,
			DraftSchemaValidator.ValidatedDraft generatedDraft,
			List<KnowledgeDocument> documents,
			String draftModelName
	) {
		Complaint complaint = getComplaint(complaintId);
		OfficialDraft draft = officialDraftRepository.save(new OfficialDraft(
				complaint, generatedDraft.renderedText(), draftModelName
		));
		List<RagContext> contexts = documents.stream()
				.map(document -> new RagContext(
						complaint,
						draft,
						document,
						document.getLegalBasis(),
						snippet(document.getContent())
				))
				.map(ragContextRepository::save)
				.toList();
		for (int index = 0; index < generatedDraft.claims().size(); index++) {
			DraftSchemaValidator.ValidatedClaim claim = generatedDraft.claims().get(index);
			draftClaimRepository.save(new DraftClaim(
					draft, index, claim.text(), claim.claimType(), claim.sourceDocumentIds()
			));
		}
		complaint.markDraftReview();
		complaintRepository.save(complaint);
		return toDraftResponse(draft, contexts);
	}

	private void blockForVerification(
			UUID complaintId,
			egovframework.example.complaint.domain.WorkflowBlocker blocker,
			String ruleCode,
			String message
	) {
		transactionTemplate.executeWithoutResult(status -> {
			Complaint complaint = getComplaint(complaintId);
			complaint.block(blocker);
			complaintRepository.save(complaint);
			verificationResultRepository.save(new VerificationResult(
					complaintId, null, ruleCode, "FAILED", message, true
			));
		});
	}

	@Transactional(readOnly = true)
	public ComplaintAnalysisResponse findAnalysis(UUID id) {
		getComplaint(id);
		return complaintAnalysisRepository.findByComplaintId(id)
				.map(this::toAnalysisResponse)
				.orElse(null);
	}

	@Transactional(readOnly = true)
	public DraftResponse findLatestDraft(UUID id) {
		getComplaint(id);
		return officialDraftRepository.findByComplaintIdOrderByCreatedAtDesc(id).stream()
				.findFirst()
				.map(draft -> toDraftResponse(draft, ragContextRepository.findByOfficialDraftIdOrderByIdAsc(draft.getId())))
				.orElse(null);
	}

	public AttachmentResponse addAttachment(UUID id, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Attachment file is required");
		}
		Complaint complaint = getComplaint(id);
		StoredFile storedFile = null;
		ComplaintAttachment savedAttachment = null;
		try {
			byte[] bytes = file.getBytes();
			AttachmentSecurityService.Inspection inspection = attachmentSecurityService.inspect(
					file.getOriginalFilename(), file.getContentType(), bytes
			);
			storedFile = fileStorageService.store(
					file.getOriginalFilename(),
					inspection.detectedType(),
					bytes.length,
					new ByteArrayInputStream(bytes)
			);
			ComplaintAttachment attachment = new ComplaintAttachment(
					complaint,
					storedFile.originalFilename(),
					inspection.detectedType(),
					storedFile.size(),
					storedFile.storageKey()
			);
			savedAttachment = complaintAttachmentRepository.saveAndFlush(attachment);
			attachmentAnalysisRepository.saveAndFlush(new AttachmentAnalysis(savedAttachment, inspection.detectedType()));
			complaint.recordAttachmentChange();
			complaintRepository.saveAndFlush(complaint);
			return AttachmentResponse.from(savedAttachment);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read attachment file", exception);
		}
		catch (RuntimeException exception) {
			if (savedAttachment != null) {
				attachmentAnalysisRepository.findByAttachment_Id(savedAttachment.getId())
						.ifPresent(attachmentAnalysisRepository::delete);
				complaintAttachmentRepository.deleteById(savedAttachment.getId());
			}
			if (storedFile != null) {
				try {
					fileStorageService.delete(storedFile.storageKey());
				}
				catch (RuntimeException cleanupException) {
					exception.addSuppressed(cleanupException);
				}
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public List<AttachmentResponse> findAttachments(UUID id) {
		getComplaint(id);
		return complaintAttachmentRepository.findByComplaint_IdOrderByCreatedAtDesc(id).stream()
				.map(AttachmentResponse::from)
				.toList();
	}

	public void deleteAttachment(UUID complaintId, UUID attachmentId) {
		Complaint complaint = getComplaint(complaintId);
		ComplaintAttachment attachment = complaintAttachmentRepository.findById(attachmentId)
				.filter(candidate -> candidate.getComplaintId().equals(complaintId))
				.orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
		attachmentAnalysisRepository.findByAttachment_Id(attachmentId)
				.ifPresent(attachmentAnalysisRepository::delete);
		fileStorageService.delete(attachment.getStorageKey());
		complaintAttachmentRepository.delete(attachment);
		complaint.recordAttachmentChange();
		complaintRepository.saveAndFlush(complaint);
	}

	private ComplaintAnalysis createAnalysis(Complaint complaint) {
		return transactionTemplate.execute(status -> createRuleBasedAnalysis(getComplaint(complaint.getId())));
	}

	private ComplaintAnalysis createRuleBasedAnalysis(Complaint complaint) {
		String text = complaint.getRedactedText().toLowerCase(Locale.ROOT);
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
		String geoJson = null;
		Map<String, Object> issue = new LinkedHashMap<>();
		issue.put("summary", intent);
		issue.put("complaintType", complaintType.name());
		boolean isPilotType = complaintType == ComplaintType.ILLEGAL_DUMPING
				|| complaintType == ComplaintType.ROAD_DAMAGE
				|| complaintType == ComplaintType.ILLEGAL_PARKING;
		String jurisdictionStatus = isPilotType ? "PILOT_CANDIDATE" : "NEEDS_JURISDICTION";
		issue.put("jurisdictionStatus", jurisdictionStatus);
		issue.put("safetyRisk", urgency == Urgency.EMERGENCY ? "EMERGENCY" : urgency == Urgency.HIGH ? "HIGH" : "NORMAL");
		issue.put("expressionRisk", "NORMAL");
		issue.put("processability", complaint.getLocationText() == null || complaint.getLocationText().isBlank()
				? "NEEDS_LOCATION" : "PROCESSABLE");
		issue.put("departmentCandidates", List.of(department.getCode()));
		issue.put("locationCandidates", complaint.getLocationText() == null || complaint.getLocationText().isBlank()
				? List.of() : List.of(complaint.getLocationText()));
		issue.put("evidenceIds", List.of());
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("schemaVersion", "complaint-support-v1");
		root.put("intent", intent);
		root.put("urgency", urgency.name());
		root.put("sentiment", sentiment.name());
		root.put("departmentCode", department.getCode());
		root.put("locationText", complaint.getLocationText());
		root.put("keywords", keywordsFor(complaintType));
		root.put("requiredAction", "Field verification and department routing");
		root.put("issues", List.of(issue));
		String analysisJson = jsonValue(root);
		analysisSchemaValidator.validate(new ComplaintAnalysisResult(
				intent,
				urgency.name(),
				sentiment.name(),
				department.getCode(),
				complaint.getLocationText(),
				null,
				analysisJson
		));
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
		complaint.markTriageReview();
		if (complaint.getLocationText() == null || complaint.getLocationText().isBlank()) {
			complaint.block(egovframework.example.complaint.domain.WorkflowBlocker.NEEDS_LOCATION);
		}
		complaintRepository.save(complaint);
		return analysis;
	}

	private ComplaintAnalysis createAnalysisFromResult(Complaint complaint, ComplaintAnalysisResult result) {
		analysisSchemaValidator.validate(result);
		if (redactionService.containsSensitivePattern(result.analysisJson())) {
			throw new IllegalStateException("Analysis schema validation failed: output contained recognizable PII");
		}
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
				null,
				result.analysisJson()
		));
		complaint.markTriageReview();
		if (complaint.getLocationText() == null || complaint.getLocationText().isBlank()) {
			complaint.block(egovframework.example.complaint.domain.WorkflowBlocker.NEEDS_LOCATION);
		}
		complaintRepository.save(complaint);
		return analysis;
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
				document.getPurpose().name(),
				document.getVerificationStatus().name(),
				document.getJurisdictionCode(),
				document.getEffectiveFrom() == null ? null : document.getEffectiveFrom().toString(),
				document.getEffectiveTo() == null ? null : document.getEffectiveTo().toString(),
				resolveSourceUrl(document.getSourceUrl())
		);
	}

	private static String resolveSourceUrl(String sourceUrl) {
		if (sourceUrl == null) {
			return null;
		}
		String normalized = sourceUrl.replace("\\", "/");
		int dataIdx = normalized.indexOf("ai-rag-engine/data/");
		if (dataIdx != -1) {
			String subPath = normalized.substring(dataIdx + "ai-rag-engine/data/".length());
			String baseUrl = "http://localhost:8081";
			try {
				if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() != null) {
					baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
				}
			} catch (Exception e) {
				// Fallback to default
			}
			return baseUrl + "/data/" + subPath;
		}
		return sourceUrl;
	}

	private Complaint getComplaint(UUID id) {
		return complaintRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + id));
	}

	private Specification<Complaint> filterByStatusAndDepartmentAndUrgency(
			ComplaintStatus status, String departmentCode, String urgencyStr) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(criteriaBuilder.equal(root.get("status"), status));
			}
			if (StringUtils.hasText(departmentCode) || StringUtils.hasText(urgencyStr)) {
				jakarta.persistence.criteria.Join<Complaint, ComplaintAnalysis> analysisJoin = root.join("analysis", jakarta.persistence.criteria.JoinType.INNER);
				if (StringUtils.hasText(departmentCode)) {
					jakarta.persistence.criteria.Join<ComplaintAnalysis, Department> departmentJoin = analysisJoin.join("department", jakarta.persistence.criteria.JoinType.INNER);
					predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(departmentJoin.get("code")), departmentCode.trim().toLowerCase(Locale.ROOT)));
				}
				if (StringUtils.hasText(urgencyStr)) {
					try {
						Urgency urgencyEnum = Urgency.valueOf(urgencyStr.trim().toUpperCase(Locale.ROOT));
						predicates.add(criteriaBuilder.equal(analysisJoin.get("urgency"), urgencyEnum));
					} catch (IllegalArgumentException e) {
						predicates.add(criteriaBuilder.disjunction());
					}
				}
			}
			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
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

	private List<String> keywordsFor(ComplaintType complaintType) {
		return switch (complaintType) {
			case ILLEGAL_DUMPING -> List.of("쓰레기", "폐기물", "무단투기", "현장확인");
			case ROAD_DAMAGE -> List.of("도로", "포트홀", "파손", "보수", "현장점검");
			case ILLEGAL_PARKING -> List.of("불법주정차", "주차", "단속", "교통");
			case TRAFFIC_SIGN -> List.of("교통표지", "신호", "정비");
			case NOISE -> List.of("소음", "생활불편", "현장확인");
			case ENVIRONMENT -> List.of("환경", "오염", "생활불편", "현장확인");
			case HAZARDOUS_MATERIAL -> List.of(
					"생화학", "위험물", "폭탄", "폭발물", "화학물질", "유해물질", "재난", "경찰", "소방"
			);
			default -> List.of("민원", "접수", "담당부서");
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

	private String jsonValue(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to encode rule-based analysis value", exception);
		}
	}

	private boolean hasRequiredOfficialMetadata(KnowledgeDocument document) {
		return StringUtils.hasText(document.getLegalBasis())
				&& StringUtils.hasText(document.getSourceUrl())
				&& StringUtils.hasText(document.getSourceVersion())
				&& StringUtils.hasText(document.getContentHash())
				&& document.getContentHash().equals(contentHashService.sha256(document.getContent()));
	}

	private boolean hasOverlappingOfficialConflicts(List<KnowledgeDocument> documents) {
		Map<String, Set<String>> contentByLegalBasis = new HashMap<>();
		for (KnowledgeDocument document : documents) {
			String basis = document.getLegalBasis() == null ? "" : document.getLegalBasis().trim();
			contentByLegalBasis.computeIfAbsent(basis, ignored -> new HashSet<>())
					.add(document.getContent());
		}
		return contentByLegalBasis.values().stream().anyMatch(contents -> contents.size() > 1);
	}

	private String modelName() {
		return MOCK_MODEL_NAME;
	}

	List<String> approvedAttachmentTexts(UUID complaintId) {
		int maxTotalChars = 20_000;
		int used = 0;
		List<String> result = new ArrayList<>();
		for (AttachmentAnalysis analysis : attachmentAnalysisRepository
				.findByAttachment_Complaint_IdAndApprovedForAiTrueOrderByCreatedAtAsc(complaintId)) {
			if (!analysis.isApprovedForAi() || !StringUtils.hasText(analysis.getOcrText()) || used >= maxTotalChars) {
				continue;
			}
			String text = analysis.getOcrText();
			int remaining = maxTotalChars - used;
			String bounded = text.length() <= remaining ? text : text.substring(0, remaining);
			result.add(bounded);
			used += bounded.length();
		}
		return List.copyOf(result);
	}

}
