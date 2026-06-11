package egovframework.example.complaint.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import egovframework.example.complaint.api.dto.ProcessingJobResponse;
import egovframework.example.complaint.api.dto.WorkerClaimRequest;
import egovframework.example.complaint.api.dto.WorkerFailureRequest;
import egovframework.example.complaint.api.dto.WorkerJobResponse;
import egovframework.example.complaint.api.dto.WorkerResultRequest;
import egovframework.example.complaint.api.dto.WorkerSupportResultRequest;
import egovframework.example.complaint.domain.AiRun;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.ProcessingJob;
import egovframework.example.complaint.domain.ProcessingJobStatus;
import egovframework.example.complaint.domain.ProcessingJobType;
import egovframework.example.complaint.domain.VerificationResult;
import egovframework.example.complaint.domain.WorkflowAuditEvent;
import egovframework.example.complaint.domain.WorkflowBlocker;
import egovframework.example.complaint.repository.AiRunRepository;
import egovframework.example.complaint.repository.ComplaintAnalysisRepository;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import egovframework.example.complaint.repository.ProcessingJobRepository;
import egovframework.example.complaint.repository.VerificationResultRepository;
import egovframework.example.complaint.repository.WorkflowAuditEventRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerJobService {

	private static final Set<ProcessingJobType> AI_JOB_TYPES = Set.of(
			ProcessingJobType.CLASSIFY_ISSUES,
			ProcessingJobType.DRAFT
	);
	private static final Set<ProcessingJobType> SUPPORT_JOB_TYPES = Set.of(
			ProcessingJobType.REDACT,
			ProcessingJobType.EXTRACT_ATTACHMENT,
			ProcessingJobType.RETRIEVE,
			ProcessingJobType.VERIFY
	);
	private static final Set<ProcessingJobType> CLAIMABLE_JOB_TYPES =
			Set.copyOf(java.util.EnumSet.allOf(ProcessingJobType.class));
	private static final Set<String> ALLOWED_PROVIDERS = Set.of("mock", "openai", "bedrock");

	private final ProcessingJobRepository processingJobRepository;
	private final ComplaintRepository complaintRepository;
	private final ComplaintAnalysisRepository complaintAnalysisRepository;
	private final KnowledgeDocumentRepository knowledgeDocumentRepository;
	private final ComplaintService complaintService;
	private final ProcessingJobRunner processingJobRunner;
	private final AiRunRepository aiRunRepository;
	private final WorkflowAuditEventRepository workflowAuditEventRepository;
	private final VerificationResultRepository verificationResultRepository;
	private final ContentHashService hashService;
	private final ObjectMapper objectMapper;
	private final AnalysisSchemaValidator analysisSchemaValidator;
	private final DraftSchemaValidator draftSchemaValidator;
	private final RedactionService redactionService;
	private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
	private final long leaseSeconds;
	private final long retryBaseSeconds;
	private final long retryMaxSeconds;

	public WorkerJobService(
			ProcessingJobRepository processingJobRepository,
			ComplaintRepository complaintRepository,
			ComplaintAnalysisRepository complaintAnalysisRepository,
			KnowledgeDocumentRepository knowledgeDocumentRepository,
			ComplaintService complaintService,
			ProcessingJobRunner processingJobRunner,
			AiRunRepository aiRunRepository,
			WorkflowAuditEventRepository workflowAuditEventRepository,
			VerificationResultRepository verificationResultRepository,
			ContentHashService hashService,
			ObjectMapper objectMapper,
			AnalysisSchemaValidator analysisSchemaValidator,
			DraftSchemaValidator draftSchemaValidator,
			RedactionService redactionService,
			org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
			@Value("${app.worker.lease-seconds:300}") long leaseSeconds,
			@Value("${app.worker.retry-base-seconds:2}") long retryBaseSeconds,
			@Value("${app.worker.retry-max-seconds:60}") long retryMaxSeconds
	) {
		this.processingJobRepository = processingJobRepository;
		this.complaintRepository = complaintRepository;
		this.complaintAnalysisRepository = complaintAnalysisRepository;
		this.knowledgeDocumentRepository = knowledgeDocumentRepository;
		this.complaintService = complaintService;
		this.processingJobRunner = processingJobRunner;
		this.aiRunRepository = aiRunRepository;
		this.workflowAuditEventRepository = workflowAuditEventRepository;
		this.verificationResultRepository = verificationResultRepository;
		this.hashService = hashService;
		this.objectMapper = objectMapper;
		this.analysisSchemaValidator = analysisSchemaValidator;
		this.draftSchemaValidator = draftSchemaValidator;
		this.redactionService = redactionService;
		this.jdbcTemplate = jdbcTemplate;
		this.leaseSeconds = Math.max(30, Math.min(leaseSeconds, 1800));
		this.retryBaseSeconds = Math.max(1, retryBaseSeconds);
		this.retryMaxSeconds = Math.max(this.retryBaseSeconds, retryMaxSeconds);
	}

	@Transactional
	public WorkerJobResponse claim(WorkerClaimRequest request) {
		Set<ProcessingJobType> requested = requestedTypes(request.jobTypes());
		List<ProcessingJob> candidates = new ArrayList<>();
		candidates.addAll(processingJobRepository.findByStatusOrderByCreatedAtAsc(ProcessingJobStatus.PENDING));
		candidates.addAll(processingJobRepository.findByStatusOrderByCreatedAtAsc(ProcessingJobStatus.FAILED));
		for (ProcessingJob candidate : candidates) {
			if (!requested.contains(candidate.getJobType())) {
				continue;
			}
			ProcessingJob job = processingJobRepository.findByIdForUpdate(candidate.getId()).orElse(null);
			if (job == null || !requested.contains(job.getJobType())
					|| (job.getStatus() != ProcessingJobStatus.PENDING && job.getStatus() != ProcessingJobStatus.FAILED)
					|| (job.getStatus() == ProcessingJobStatus.FAILED && job.getLeaseUntil() != null
							&& job.getLeaseUntil().isAfter(LocalDateTime.now()))
					|| job.getAttempts() >= job.getMaxAttempts()) {
				continue;
			}
			String before = jobState(job);
			job.start(LocalDateTime.now().plusSeconds(leaseSeconds));
			ProcessingJob saved = processingJobRepository.saveAndFlush(job);
			Map<String, Object> payload;
			try {
				payload = payload(saved);
			}
			catch (WorkerPreconditionException exception) {
				saved.fail(exception.getMessage(), true);
				ProcessingJob blocked = processingJobRepository.saveAndFlush(saved);
				Complaint complaint = complaintRepository.findById(blocked.getComplaintId())
						.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + blocked.getComplaintId()));
				complaint.block(exception.blocker());
				complaintRepository.saveAndFlush(complaint);
				recordBlock(complaint.getId(), exception.blocker(), exception.getMessage());
				audit(blocked, "BLOCK_" + blocked.getJobType(), request.workerId(), before, jobState(blocked));
				continue;
			}
			audit(saved, "CLAIM_" + saved.getJobType(), request.workerId(), before, jobState(saved));
			return new WorkerJobResponse(
					saved.getId(),
					saved.getComplaintId(),
					saved.getJobType().name(),
					saved.getAttempts(),
					saved.getMaxAttempts(),
					leaseSeconds,
					hashService.sha256(json(payload)),
					payload
			);
		}
		return null;
	}

	@Transactional
	public ProcessingJobResponse submitResult(UUID jobId, WorkerResultRequest request) {
		ProcessingJob job = runningJob(jobId, AI_JOB_TYPES);
		Map<String, Object> expectedPayload = payload(job);
		String expectedInputHash = hashService.sha256(json(expectedPayload));
		if (!expectedInputHash.equals(request.inputHash())) {
			throw new IllegalStateException("Worker result input hash does not match the claimed governed input");
		}
		String outputJson = request.output().toString();
		if (!hashService.sha256(outputJson).equals(request.outputHash())) {
			throw new IllegalStateException("Worker result output hash does not match the submitted output");
		}
		String provider = request.provider().trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_PROVIDERS.contains(provider)) {
			throw new IllegalArgumentException("Unsupported worker provider");
		}
		if (!promptVersion(job.getJobType()).equals(request.promptVersion())
				|| !schemaVersion(job.getJobType()).equals(request.schemaVersion())) {
			throw new IllegalStateException("Worker result prompt and schema versions must match the governed job contract");
		}
		List<Long> evidenceDocumentIds = request.evidenceDocumentIds() == null
				? List.of()
				: List.copyOf(request.evidenceDocumentIds());
		String before = jobState(job);
		AiRun run = new AiRun(
				job.getComplaintId(),
				job.getId(),
				job.getJobType().name(),
				provider,
				request.modelName().trim(),
				request.promptVersion().trim(),
				request.schemaVersion().trim(),
				request.inputHash(),
				request.costUnits()
		);
		try {
			validateEvidenceSelection(job, expectedPayload, request.output(), evidenceDocumentIds);
			validateWorkerOutput(job, request.output(), evidenceDocumentIds);
			String resultReference = processingJobRunner.applyWorkerResult(
					job,
					request.output(),
					evidenceDocumentIds,
					request.modelName().trim()
			);
			run.succeed(request.outputHash(), request.durationMs(), request.retryCount());
			job.succeed(resultReference);
		}
		catch (RuntimeException exception) {
			String reason = exception.getMessage() == null ? "Worker result validation failed" : exception.getMessage();
			run.fail(reason, request.durationMs(), request.retryCount());
			job.fail(reason, true);
			Complaint complaint = complaintRepository.findById(job.getComplaintId())
					.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + job.getComplaintId()));
			WorkflowBlocker blocker = blockerFor(reason);
			complaint.block(blocker);
			complaintRepository.saveAndFlush(complaint);
			recordBlock(complaint.getId(), blocker, reason);
		}
		aiRunRepository.save(run);
		ProcessingJob saved = processingJobRepository.saveAndFlush(job);
		audit(saved, (saved.getStatus() == ProcessingJobStatus.SUCCEEDED ? "APPLY_" : "BLOCK_")
				+ saved.getJobType(), request.workerId(), before, jobState(saved));
		return ProcessingJobResponse.from(saved);
	}

	private void validateWorkerOutput(ProcessingJob job, JsonNode output, List<Long> evidenceDocumentIds) {
		if (job.getJobType() == ProcessingJobType.CLASSIFY_ISSUES) {
			ComplaintAnalysisResult result = new ComplaintAnalysisResult(
					output.path("intent").asText(),
					output.path("urgency").asText(),
					output.path("sentiment").asText(),
					output.path("departmentCode").asText(),
					output.path("locationText").isNull() ? null : output.path("locationText").asText(),
					null,
					output.toString()
			);
			analysisSchemaValidator.validate(result);
			if (redactionService.containsSensitivePattern(result.analysisJson())) {
				throw new IllegalStateException("Analysis schema validation failed: output contained recognizable PII");
			}
			return;
		}
		List<KnowledgeDocument> documents = knowledgeDocumentRepository.findAllById(evidenceDocumentIds);
		if (documents.size() != evidenceDocumentIds.size()) {
			throw new IllegalStateException("Draft worker selected unknown evidence documents");
		}
		DraftSchemaValidator.ValidatedDraft draft = draftSchemaValidator.validate(output.toString(), documents);
		if (redactionService.containsSensitivePattern(draft.renderedText())) {
			throw new IllegalStateException("Draft schema validation failed: output contained recognizable PII");
		}
	}

	@Transactional
	public ProcessingJobResponse submitSupportResult(UUID jobId, WorkerSupportResultRequest request) {
		ProcessingJob job = runningJob(jobId, SUPPORT_JOB_TYPES);
		String expectedInputHash = hashService.sha256(json(payload(job)));
		if (!expectedInputHash.equals(request.inputHash())) {
			throw new IllegalStateException("Worker result input hash does not match the claimed governed input");
		}
		String before = jobState(job);
		job.succeed(request.resultReference().trim());
		ProcessingJob saved = processingJobRepository.saveAndFlush(job);
		audit(saved, "APPLY_" + saved.getJobType(), request.workerId(), before, jobState(saved));
		return ProcessingJobResponse.from(saved);
	}

	@Transactional
	public ProcessingJobResponse submitFailure(UUID jobId, WorkerFailureRequest request) {
		ProcessingJob job = runningJob(jobId, CLAIMABLE_JOB_TYPES);
		String before = jobState(job);
		boolean blocked = !request.retryable() || job.getAttempts() >= job.getMaxAttempts();
		job.fail(
				request.reason(),
				blocked,
				blocked ? null : LocalDateTime.now().plusSeconds(retryDelaySeconds(job.getAttempts()))
		);
		ProcessingJob saved = processingJobRepository.saveAndFlush(job);
		if (blocked) {
			Complaint complaint = complaintRepository.findById(saved.getComplaintId())
					.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + saved.getComplaintId()));
			complaint.block(WorkflowBlocker.PROCESSING_FAILED);
			complaintRepository.saveAndFlush(complaint);
			recordBlock(complaint.getId(), WorkflowBlocker.PROCESSING_FAILED, request.reason());
		}
		audit(saved, (blocked ? "BLOCK_" : "FAIL_") + saved.getJobType(), request.workerId(), before, jobState(saved));
		return ProcessingJobResponse.from(saved);
	}

	private long retryDelaySeconds(int attempts) {
		long multiplier = 1L << Math.min(20, Math.max(0, attempts - 1));
		return multiplier > retryMaxSeconds / retryBaseSeconds
				? retryMaxSeconds
				: Math.min(retryMaxSeconds, retryBaseSeconds * multiplier);
	}

	private ProcessingJob runningJob(UUID jobId, Set<ProcessingJobType> allowedTypes) {
		ProcessingJob job = processingJobRepository.findByIdForUpdate(jobId)
				.orElseThrow(() -> new EntityNotFoundException("Processing job not found: " + jobId));
		if (job.getStatus() != ProcessingJobStatus.RUNNING) {
			throw new IllegalStateException("Worker result can only be submitted for a running job");
		}
		if (!allowedTypes.contains(job.getJobType())) {
			throw new IllegalStateException("This internal result contract does not accept the job type");
		}
		if (job.getLeaseUntil() == null || !job.getLeaseUntil().isAfter(LocalDateTime.now())) {
			throw new IllegalStateException("Worker lease expired before result submission");
		}
		return job;
	}

	private Set<ProcessingJobType> requestedTypes(List<String> values) {
		if (values == null || values.isEmpty()) {
			return CLAIMABLE_JOB_TYPES;
		}
		java.util.HashSet<ProcessingJobType> result = new java.util.HashSet<>();
		for (String value : values) {
			try {
				ProcessingJobType type = ProcessingJobType.valueOf(value.trim().toUpperCase(Locale.ROOT));
				if (CLAIMABLE_JOB_TYPES.contains(type)) {
					result.add(type);
				}
			}
			catch (RuntimeException ignored) {
				// Unsupported types are never claimable through the internal worker contract.
			}
		}
		if (result.isEmpty()) {
			throw new IllegalArgumentException("At least one supported worker job type is required");
		}
		return Set.copyOf(result);
	}

	private Map<String, Object> payload(ProcessingJob job) {
		Complaint complaint = complaintRepository.findById(job.getComplaintId())
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + job.getComplaintId()));
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("contractVersion", "python-worker-input-v1");
		payload.put("jobType", job.getJobType().name());
		payload.put("complaintId", complaint.getId().toString());
		if (job.getPayloadReference() != null && !job.getPayloadReference().isBlank()) {
			payload.put("payloadReference", job.getPayloadReference());
		}
		if (AI_JOB_TYPES.contains(job.getJobType()) || job.getJobType() == ProcessingJobType.REDACT) {
			payload.put("redactedText", complaint.getRedactedText());
		}
		if (AI_JOB_TYPES.contains(job.getJobType())) {
			payload.put("locationText", complaint.getLocationText());
			payload.put("approvedAttachmentTexts", complaintService.approvedAttachmentTexts(complaint.getId()));
		}
		if (job.getJobType() == ProcessingJobType.DRAFT) {
			var analysis = complaintAnalysisRepository.findByComplaintId(complaint.getId())
					.orElseThrow(() -> new IllegalStateException("A validated analysis is required before drafting"));
			payload.put("analysis", parse(analysis.getAnalysisJson()));
			List<Map<String, Object>> candidates = eligibleKnowledgeCandidates(analysis);
			validateDraftCandidates(candidates, complaint.getId());
			payload.put("knowledgeCandidates", candidates);
		}
		return payload;
	}

	private List<Map<String, Object>> eligibleKnowledgeCandidates(ComplaintAnalysis analysis) {
		LocalDate today = LocalDate.now();
		Map<Long, KnowledgeDocument> candidates = new LinkedHashMap<>();
		for (String keyword : governedCandidateKeywords(analysis)) {
			knowledgeDocumentRepository.searchByKeyword(keyword)
					.forEach(document -> candidates.put(document.getId(), document));
		}
		boolean hasRegulatoryIssue = jdbcTemplate.queryForObject(
				"select count(*) from complaint_issues where complaint_id = ? and complaint_type in ('ILLEGAL_PARKING', 'ILLEGAL_DUMPING', 'HAZARDOUS_MATERIAL')",
				Integer.class,
				analysis.getComplaint().getId()
		) > 0;
		boolean requiresOfficialLaw = hasRegulatoryIssue;

		return candidates.values().stream()
				.filter(document -> {
					if (requiresOfficialLaw) {
						return document.isOfficialLegalEvidence(today);
					} else {
						return document.isOfficialLegalEvidence(today)
								|| document.getPurpose() == egovframework.example.complaint.domain.KnowledgePurpose.LOCAL_ORDINANCE_REFERENCE
								|| document.getPurpose() == egovframework.example.complaint.domain.KnowledgePurpose.PROCEDURE;
					}
				})
				.filter(document -> {
					if (requiresOfficialLaw) {
						return document.getLegalBasis() != null && !document.getLegalBasis().isBlank()
								&& document.getSourceUrl() != null && !document.getSourceUrl().isBlank()
								&& document.getSourceVersion() != null && !document.getSourceVersion().isBlank()
								&& document.getContentHash() != null
								&& document.getContentHash().equals(hashService.sha256(document.getContent()));
					} else {
						return document.getContentHash() != null
								&& document.getContentHash().equals(hashService.sha256(document.getContent()));
					}
				})
				.limit(200)
				.map(this::knowledgeCandidate)
				.toList();
	}

	private void validateDraftCandidates(List<Map<String, Object>> candidates, UUID complaintId) {
		boolean hasRegulatoryIssue = jdbcTemplate.queryForObject(
				"select count(*) from complaint_issues where complaint_id = ? and complaint_type in ('ILLEGAL_PARKING', 'ILLEGAL_DUMPING', 'HAZARDOUS_MATERIAL')",
				Integer.class,
				complaintId
		) > 0;
		boolean requiresOfficialLaw = hasRegulatoryIssue;

		if (requiresOfficialLaw && candidates.isEmpty()) {
			throw new WorkerPreconditionException(
					WorkflowBlocker.EVIDENCE_INSUFFICIENT,
					"Verified official evidence is required before a draft worker can be claimed"
			);
		}
		Map<String, Set<String>> hashesByLegalBasis = new LinkedHashMap<>();
		for (Map<String, Object> candidate : candidates) {
			Object legalBasisObj = candidate.get("legalBasis");
			if (legalBasisObj == null) {
				continue;
			}
			String legalBasis = String.valueOf(legalBasisObj).trim();
			if (legalBasis.isEmpty() || "null".equals(legalBasis)) {
				continue;
			}
			String contentHash = String.valueOf(candidate.get("contentHash")).trim();
			hashesByLegalBasis.computeIfAbsent(legalBasis, ignored -> new LinkedHashSet<>()).add(contentHash);
		}
		if (requiresOfficialLaw && hashesByLegalBasis.values().stream().anyMatch(hashes -> hashes.size() > 1)) {
			throw new WorkerPreconditionException(
					WorkflowBlocker.CONFLICT_DETECTED,
					"Conflicting official evidence must be resolved before a draft worker can be claimed"
			);
		}
	}

	private List<String> governedCandidateKeywords(ComplaintAnalysis analysis) {
		LinkedHashSet<String> keywords = new LinkedHashSet<>();

		// 1. Add specific toilet keywords if toilet-related terms are present (fail-safe RAG injection)
		String intent = analysis.getIntent() != null ? analysis.getIntent() : "";
		String rawText = (analysis.getComplaint() != null && analysis.getComplaint().getRawText() != null) ? analysis.getComplaint().getRawText() : "";
		String redactedText = (analysis.getComplaint() != null && analysis.getComplaint().getRedactedText() != null) ? analysis.getComplaint().getRedactedText() : "";
		if (intent.contains("화장실") || intent.contains("toilet") || intent.contains("restroom") ||
				rawText.contains("화장실") || rawText.contains("toilet") || rawText.contains("restroom") ||
				redactedText.contains("화장실") || redactedText.contains("toilet") || redactedText.contains("restroom")) {
			keywords.add("공중화장실");
			keywords.add("화장실");
		}

		// 2. Add specific keywords from LLM analysis first
		JsonNode analysisJson = parse(analysis.getAnalysisJson());
		analysisJson.path("keywords").forEach(value -> {
			if (value.isTextual() && !value.asText().isBlank()) {
				keywords.add(value.asText());
			}
		});

		// 3. Add intent
		keywords.add(analysis.getIntent());

		// 4. Add generic fallback keywords last
		keywords.addAll(switch (analysis.getComplaintType()) {
			case ILLEGAL_DUMPING -> List.of("waste", "dumping", "garbage", "trash");
			case ROAD_DAMAGE -> List.of("road", "pothole", "sidewalk");
			case ILLEGAL_PARKING -> List.of("parking", "traffic");
			case TRAFFIC_SIGN -> List.of("traffic", "signal", "sign");
			case NOISE -> List.of("noise");
			case ENVIRONMENT -> List.of("environment", "pollution");
			case HAZARDOUS_MATERIAL -> List.of("hazardous", "chemical", "safety");
			case GENERAL -> List.of("complaint");
		});

		return keywords.stream()
				.filter(value -> value != null && !value.isBlank())
				.limit(40)
				.toList();
	}

	private void validateEvidenceSelection(
			ProcessingJob job,
			Map<String, Object> expectedPayload,
			JsonNode output,
			List<Long> evidenceDocumentIds
	) {
		if (job.getJobType() == ProcessingJobType.CLASSIFY_ISSUES) {
			if (!evidenceDocumentIds.isEmpty()) {
				throw new IllegalStateException("Analysis worker results must not select draft evidence");
			}
			return;
		}
		Set<Long> selected = new LinkedHashSet<>(evidenceDocumentIds);
		if (selected.isEmpty() || selected.size() != evidenceDocumentIds.size()) {
			throw new IllegalStateException("Draft worker results must select unique governed evidence");
		}
		Set<Long> allowed = new LinkedHashSet<>();
		Object candidates = expectedPayload.get("knowledgeCandidates");
		if (candidates instanceof List<?> values) {
			for (Object value : values) {
				if (value instanceof Map<?, ?> candidate && candidate.get("id") instanceof Number id) {
					allowed.add(id.longValue());
				}
			}
		}
		if (!allowed.containsAll(selected)) {
			throw new IllegalStateException("Draft worker selected evidence outside the governed candidate set");
		}
		Set<Long> claimed = new LinkedHashSet<>();
		output.path("claims").forEach(claim -> claim.path("evidenceIds").forEach(value -> {
			try {
				claimed.add(Long.valueOf(value.asText()));
			}
			catch (NumberFormatException ignored) {
				// The versioned draft schema validator reports malformed evidence IDs.
			}
		}));
		if (!claimed.equals(selected)) {
			throw new IllegalStateException("Draft worker evidence selection must match the submitted claim evidence IDs");
		}
	}

	private String promptVersion(ProcessingJobType jobType) {
		return jobType == ProcessingJobType.DRAFT ? "evidence-draft-prompt-v1" : "issue-analysis-prompt-v1";
	}

	private String schemaVersion(ProcessingJobType jobType) {
		return jobType == ProcessingJobType.DRAFT ? "draft-claims-v1" : "complaint-support-v1";
	}

	private WorkflowBlocker blockerFor(String reason) {
		String normalized = reason.toLowerCase(Locale.ROOT);
		if (normalized.contains("conflict")) {
			return WorkflowBlocker.CONFLICT_DETECTED;
		}
		if (normalized.contains("jurisdiction")) {
			return WorkflowBlocker.NEEDS_JURISDICTION;
		}
		if (normalized.contains("evidence")) {
			return WorkflowBlocker.EVIDENCE_INSUFFICIENT;
		}
		return WorkflowBlocker.PROCESSING_FAILED;
	}

	private void recordBlock(UUID complaintId, WorkflowBlocker blocker, String reason) {
		String ruleCode = switch (blocker) {
			case EVIDENCE_INSUFFICIENT -> "OFFICIAL_EVIDENCE_REQUIRED";
			case CONFLICT_DETECTED -> "CONFLICT_SCAN";
			case NEEDS_JURISDICTION -> "JURISDICTION_FILTER";
			default -> "WORKER_RESULT_FAILURE";
		};
		verificationResultRepository.save(new VerificationResult(
				complaintId,
				null,
				ruleCode,
				"FAILED",
				reason,
				true
		));
	}

	private Map<String, Object> knowledgeCandidate(KnowledgeDocument document) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("id", document.getId());
		value.put("title", document.getTitle());
		value.put("content", document.getContent());
		value.put("keywords", document.getKeywords());
		value.put("legalBasis", document.getLegalBasis());
		value.put("sourceUrl", document.getSourceUrl());
		value.put("sourceVersion", document.getSourceVersion());
		value.put("contentHash", document.getContentHash());
		value.put("jurisdictionCode", document.getJurisdictionCode());
		value.put("effectiveFrom", document.getEffectiveFrom() == null ? null : document.getEffectiveFrom().toString());
		value.put("effectiveTo", document.getEffectiveTo() == null ? null : document.getEffectiveTo().toString());
		return value;
	}

	private JsonNode parse(String value) {
		try {
			return StrictJson.readSingleDocument(objectMapper, value, "governed analysis");
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("Worker result must contain valid governed analysis JSON", exception);
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to encode worker input contract", exception);
		}
	}

	private void audit(ProcessingJob job, String action, String workerId, String before, String after) {
		workflowAuditEventRepository.save(new WorkflowAuditEvent(
				"PROCESSING_JOB",
				job.getId().toString(),
				action,
				workerId,
				"SYSTEM_WORKER",
				before,
				after,
				job.getIdempotencyKey()
		));
	}

	private String jobState(ProcessingJob job) {
		return "{\"status\":\"" + job.getStatus() + "\",\"attempts\":" + job.getAttempts()
				+ ",\"version\":" + job.getVersion() + "}";
	}

	private static final class WorkerPreconditionException extends RuntimeException {

		private final WorkflowBlocker blocker;

		private WorkerPreconditionException(WorkflowBlocker blocker, String message) {
			super(message);
			this.blocker = blocker;
		}

		private WorkflowBlocker blocker() {
			return blocker;
		}
	}
}
