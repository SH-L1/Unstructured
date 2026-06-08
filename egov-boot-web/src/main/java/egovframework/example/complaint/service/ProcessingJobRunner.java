package egovframework.example.complaint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.DraftResponse;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.AiRun;
import egovframework.example.complaint.domain.ComplaintIssue;
import egovframework.example.complaint.domain.ComplaintType;
import egovframework.example.complaint.domain.DepartmentTask;
import egovframework.example.complaint.domain.DraftClaim;
import egovframework.example.complaint.domain.EvidenceSnapshot;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.LocationCandidate;
import egovframework.example.complaint.domain.OfficialDraft;
import egovframework.example.complaint.domain.ProcessingJob;
import egovframework.example.complaint.domain.ProcessingJobType;
import egovframework.example.complaint.domain.RagContext;
import egovframework.example.complaint.domain.RetrievalRun;
import egovframework.example.complaint.domain.VerificationResult;
import egovframework.example.complaint.domain.WorkflowAuditEvent;
import egovframework.example.complaint.domain.WorkflowBlocker;
import egovframework.example.complaint.repository.ComplaintIssueRepository;
import egovframework.example.complaint.repository.ComplaintAnalysisRepository;
import egovframework.example.complaint.repository.AiRunRepository;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.DepartmentTaskRepository;
import egovframework.example.complaint.repository.DraftClaimRepository;
import egovframework.example.complaint.repository.EvidenceSnapshotRepository;
import egovframework.example.complaint.repository.OfficialDraftRepository;
import egovframework.example.complaint.repository.ProcessingJobRepository;
import egovframework.example.complaint.repository.RagContextRepository;
import egovframework.example.complaint.repository.RetrievalRunRepository;
import egovframework.example.complaint.repository.LocationCandidateRepository;
import egovframework.example.complaint.repository.VerificationResultRepository;
import egovframework.example.complaint.repository.WorkflowAuditEventRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProcessingJobRunner {

	private final ProcessingJobRepository processingJobRepository;
	private final ComplaintRepository complaintRepository;
	private final ComplaintAnalysisRepository complaintAnalysisRepository;
	private final ComplaintIssueRepository complaintIssueRepository;
	private final DepartmentTaskRepository departmentTaskRepository;
	private final LocationCandidateRepository locationCandidateRepository;
	private final ComplaintService complaintService;
	private final OfficialDraftRepository officialDraftRepository;
	private final RagContextRepository ragContextRepository;
	private final EvidenceSnapshotRepository evidenceSnapshotRepository;
	private final DraftClaimRepository draftClaimRepository;
	private final VerificationResultRepository verificationResultRepository;
	private final ContentHashService hashService;
	private final JdbcTemplate jdbcTemplate;
	private final AiRunRepository aiRunRepository;
	private final RetrievalRunRepository retrievalRunRepository;
	private final ObjectMapper objectMapper;
	private final RedactionService redactionService;
	private final TransactionTemplate transactionTemplate;
	private final WorkflowAuditEventRepository workflowAuditEventRepository;

	public ProcessingJobRunner(
			ProcessingJobRepository processingJobRepository,
			ComplaintRepository complaintRepository,
			ComplaintAnalysisRepository complaintAnalysisRepository,
			ComplaintIssueRepository complaintIssueRepository,
			DepartmentTaskRepository departmentTaskRepository,
			LocationCandidateRepository locationCandidateRepository,
			ComplaintService complaintService,
			OfficialDraftRepository officialDraftRepository,
			RagContextRepository ragContextRepository,
			EvidenceSnapshotRepository evidenceSnapshotRepository,
			DraftClaimRepository draftClaimRepository,
			VerificationResultRepository verificationResultRepository,
			ContentHashService hashService,
			JdbcTemplate jdbcTemplate,
			AiRunRepository aiRunRepository,
			RetrievalRunRepository retrievalRunRepository,
			ObjectMapper objectMapper,
			RedactionService redactionService,
			TransactionTemplate transactionTemplate,
			WorkflowAuditEventRepository workflowAuditEventRepository
	) {
		this.processingJobRepository = processingJobRepository;
		this.complaintRepository = complaintRepository;
		this.complaintAnalysisRepository = complaintAnalysisRepository;
		this.complaintIssueRepository = complaintIssueRepository;
		this.departmentTaskRepository = departmentTaskRepository;
		this.locationCandidateRepository = locationCandidateRepository;
		this.complaintService = complaintService;
		this.officialDraftRepository = officialDraftRepository;
		this.ragContextRepository = ragContextRepository;
		this.evidenceSnapshotRepository = evidenceSnapshotRepository;
		this.draftClaimRepository = draftClaimRepository;
		this.verificationResultRepository = verificationResultRepository;
		this.hashService = hashService;
		this.jdbcTemplate = jdbcTemplate;
		this.aiRunRepository = aiRunRepository;
		this.retrievalRunRepository = retrievalRunRepository;
		this.objectMapper = objectMapper;
		this.redactionService = redactionService;
		this.transactionTemplate = transactionTemplate;
		this.workflowAuditEventRepository = workflowAuditEventRepository;
	}

	@Async
	public void runAsync(UUID jobId) {
		while (true) {
			ProcessingJob job = start(jobId);
			try {
				String resultReference = execute(job);
				succeed(jobId, resultReference);
				return;
			}
			catch (IllegalStateException exception) {
				boolean evidenceBlocked = exception.getMessage() != null
						&& exception.getMessage().contains("Verified official evidence");
				boolean conflictBlocked = exception.getMessage() != null
						&& exception.getMessage().contains("Conflicting official evidence");
				boolean schemaBlocked = exception.getMessage() != null
						&& exception.getMessage().contains("schema validation failed");
				if (evidenceBlocked) {
					fail(jobId, exception.getMessage(), true, WorkflowBlocker.EVIDENCE_INSUFFICIENT);
					return;
				}
				if (conflictBlocked) {
					fail(jobId, exception.getMessage(), true, WorkflowBlocker.CONFLICT_DETECTED);
					return;
				}
				if (schemaBlocked) {
					fail(jobId, exception.getMessage(), true, WorkflowBlocker.PROCESSING_FAILED);
					return;
				}
				if (job.getAttempts() >= job.getMaxAttempts()) {
					fail(jobId, exception.getMessage(), true, WorkflowBlocker.PROCESSING_FAILED);
					return;
				}
				fail(jobId, exception.getMessage(), false, null);
				backoff(job.getAttempts());
			}
			catch (WorkflowGateException exception) {
				fail(jobId, exception.getMessage(), true, exception.blocker());
				return;
			}
			catch (RuntimeException exception) {
				fail(jobId, exception.getMessage(), true, WorkflowBlocker.PROCESSING_FAILED);
				return;
			}
		}
	}

	private void backoff(int attempts) {
		long delayMillis = Math.min(5_000L, 100L * (1L << Math.max(0, attempts - 1)));
		try {
			Thread.sleep(delayMillis);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted during processing retry backoff", exception);
		}
	}

	private String execute(ProcessingJob job) {
		Complaint complaint = complaintRepository.findById(job.getComplaintId())
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + job.getComplaintId()));
		AiRun aiRun = aiRunRepository.save(new AiRun(
				job.getComplaintId(),
				job.getId(),
				job.getJobType().name(),
				"embedded-mock",
				"mock-korean-civil-complaint-v1",
				promptVersion(job.getJobType()),
				schemaVersion(job.getJobType()),
				hashService.sha256(job.getJobType() + "|" + complaint.getRedactedText() + "|"
						+ String.valueOf(complaint.getLocationText())),
				estimatedCostUnits(job.getJobType())
		));
		long startedAt = System.currentTimeMillis();
		try {
			String result = switch (job.getJobType()) {
				case CLASSIFY_ISSUES -> processAnalysis(job);
				case DRAFT -> processDraft(job);
				default -> throw new IllegalStateException("No Spring executor for job type: " + job.getJobType());
			};
			aiRun.updateInputHash(hashService.sha256(inputForAudit(job, complaint, result)));
			aiRun.succeed(hashService.sha256(outputForAudit(job, result)),
					System.currentTimeMillis() - startedAt, job.getAttempts() - 1);
			aiRunRepository.save(aiRun);
			return result;
		}
		catch (RuntimeException exception) {
			aiRun.fail(exception.getMessage(), System.currentTimeMillis() - startedAt, job.getAttempts() - 1);
			aiRunRepository.save(aiRun);
			throw exception;
		}
	}

	private String outputForAudit(ProcessingJob job, String resultReference) {
		if (job.getJobType() == ProcessingJobType.CLASSIFY_ISSUES) {
			return complaintAnalysisRepository.findByComplaintId(job.getComplaintId())
					.map(egovframework.example.complaint.domain.ComplaintAnalysis::getAnalysisJson)
					.orElse(resultReference);
		}
		if (job.getJobType() == ProcessingJobType.DRAFT) {
			try {
				return officialDraftRepository.findById(Long.valueOf(resultReference))
						.map(OfficialDraft::getDraftText)
						.orElse(resultReference);
			}
			catch (NumberFormatException exception) {
				return resultReference;
			}
		}
		return resultReference;
	}

	private String inputForAudit(ProcessingJob job, Complaint complaint, String resultReference) {
		StringBuilder material = new StringBuilder()
				.append("task=").append(job.getJobType()).append('\n')
				.append("prompt=").append(promptVersion(job.getJobType())).append('\n')
				.append("schema=").append(schemaVersion(job.getJobType())).append('\n')
				.append("complaint=").append(complaint.getRedactedText()).append('\n')
				.append("location=").append(String.valueOf(complaint.getLocationText())).append('\n');
		complaintService.approvedAttachmentTexts(complaint.getId()).forEach(text ->
				material.append("approvedAttachmentHash=").append(hashService.sha256(text)).append('\n')
		);
		if (job.getJobType() == ProcessingJobType.DRAFT) {
			complaintAnalysisRepository.findByComplaintId(complaint.getId()).ifPresent(analysis ->
					material.append("analysisHash=").append(hashService.sha256(analysis.getAnalysisJson())).append('\n')
			);
			try {
				Long draftId = Long.valueOf(resultReference);
				ragContextRepository.findByOfficialDraftIdOrderByIdAsc(draftId).stream()
						.map(RagContext::getKnowledgeDocument)
						.sorted(java.util.Comparator.comparing(KnowledgeDocument::getId))
						.forEach(document -> material
								.append("evidence=").append(document.getId()).append(':')
								.append(document.getSourceVersion()).append(':')
								.append(document.getContentHash()).append('\n'));
			}
			catch (NumberFormatException ignored) {
				material.append("draftResultReference=").append(resultReference).append('\n');
			}
		}
		return material.toString();
	}

	private long estimatedCostUnits(ProcessingJobType jobType) {
		return switch (jobType) {
			case CLASSIFY_ISSUES -> 900;
			case DRAFT -> 1200;
			default -> 0;
		};
	}

	private String promptVersion(ProcessingJobType jobType) {
		return jobType == ProcessingJobType.DRAFT ? "evidence-draft-prompt-v1" : "issue-analysis-prompt-v1";
	}

	private String schemaVersion(ProcessingJobType jobType) {
		return jobType == ProcessingJobType.DRAFT ? "draft-claims-v1" : "complaint-support-v1";
	}

	private String processAnalysis(ProcessingJob job) {
		return applyAnalysis(job, complaintService.analyze(job.getComplaintId()));
	}

	public String applyWorkerResult(
			ProcessingJob job,
			JsonNode output,
			List<Long> evidenceDocumentIds,
			String modelName
	) {
		return switch (job.getJobType()) {
			case CLASSIFY_ISSUES -> applyAnalysis(
					job,
					complaintService.applyWorkerAnalysis(job.getComplaintId(), output)
			);
			case DRAFT -> applyWorkerDraft(job, output, evidenceDocumentIds, modelName);
			default -> throw new IllegalStateException("Unsupported Python worker job type: " + job.getJobType());
		};
	}

	private String applyAnalysis(ProcessingJob job, ComplaintAnalysisResponse analysis) {
		UUID complaintId = job.getComplaintId();
		String beforeComplaint = complaintState(complaintRepository.findById(complaintId)
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + complaintId)));
		List<IssueProposal> proposals = issueProposals(analysis);
		transactionTemplate.executeWithoutResult(status -> {
			Complaint complaint = complaintRepository.findById(complaintId)
					.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + complaintId));
			if (complaintIssueRepository.findByComplaint_IdOrderByIssueIndexAsc(complaintId).isEmpty()) {
				boolean needsJurisdiction = false;
				for (int index = 0; index < proposals.size(); index++) {
					IssueProposal proposal = proposals.get(index);
					String assignmentDepartment = findSyntheticAssignmentDepartment(proposal.complaintType());
					String processability = analysis.locationText() == null || analysis.locationText().isBlank()
							? "NEEDS_LOCATION"
							: assignmentDepartment == null ? "NEEDS_JURISDICTION" : proposal.processability();
					String jurisdictionStatus = assignmentDepartment == null
							? "NEEDS_JURISDICTION"
							: "SYNTHETIC_DEMO_CONFIRMED";
					needsJurisdiction = needsJurisdiction || assignmentDepartment == null;
					ComplaintIssue issue = complaintIssueRepository.save(new ComplaintIssue(
							complaint,
							index,
							proposal.summary(),
							proposal.complaintType(),
							jurisdictionStatus,
							proposal.safetyRisk(),
							proposal.expressionRisk(),
							processability
					));
					if (assignmentDepartment != null) {
						departmentTaskRepository.save(new DepartmentTask(
								issue,
								assignmentDepartment,
								"SYNTHETIC_DEMO assignment rule; human review required; score=100"
						));
					}
					List<String> candidateCodes = proposal.departmentCandidates().stream().distinct()
							.filter(ProcessingJobRunner::allowedDepartment)
							.filter(code -> !code.equals(assignmentDepartment))
							.limit(3)
							.toList();
					for (int candidateIndex = 0; candidateIndex < candidateCodes.size(); candidateIndex++) {
						String code = candidateCodes.get(candidateIndex);
						int score = Math.max(10, 80 - (candidateIndex * 20));
						departmentTaskRepository.save(new DepartmentTask(
								issue, code, "AI Top-3 candidate; staff confirmation required; score=" + score
						));
					}
					proposal.locationCandidates().stream().distinct()
							.filter(value -> value != null && !value.isBlank())
							.forEach(value -> locationCandidateRepository.save(new LocationCandidate(issue, value, "AI_EXTRACTED")));
				}
				if (needsJurisdiction && complaint.getWorkflowBlocker() == null) {
					complaint.block(WorkflowBlocker.NEEDS_JURISDICTION);
					complaintRepository.saveAndFlush(complaint);
				}
				auditIfChanged("COMPLAINT", complaintId.toString(), "ANALYSIS_APPLIED",
						beforeComplaint, complaintState(complaint), job.getIdempotencyKey());
			}
		});
		return complaintId.toString();
	}

	private List<IssueProposal> issueProposals(ComplaintAnalysisResponse analysis) {
		List<IssueProposal> proposals = new ArrayList<>();
		try {
			JsonNode issues = objectMapper.readTree(analysis.analysisJson()).path("issues");
			if (issues.isArray()) {
				for (JsonNode issue : issues) {
					proposals.add(new IssueProposal(
							text(issue, "summary", analysis.intent()),
							complaintType(text(issue, "complaintType", analysis.complaintType())),
							text(issue, "jurisdictionStatus", "NEEDS_JURISDICTION"),
							text(issue, "safetyRisk", safetyRisk(analysis)),
							text(issue, "expressionRisk", "NORMAL"),
							text(issue, "processability", "PROCESSABLE"),
							values(issue.path("departmentCandidates"), analysis.departmentCode()),
							values(issue.path("locationCandidates"), analysis.locationText())
					));
				}
			}
		}
		catch (Exception ignored) {
			// Structured providers supply issues[]. Rule-based and legacy providers use
			// the deterministic fallback below.
		}
		if (proposals.isEmpty()) {
			proposals.add(new IssueProposal(
					analysis.intent(),
					complaintType(analysis.complaintType()),
					"PILOT_CANDIDATE",
					safetyRisk(analysis),
					"ANGER".equals(analysis.sentiment()) ? "HIGH" : "NORMAL",
					"PROCESSABLE",
					List.of(analysis.departmentCode()),
					analysis.locationText() == null ? List.of() : List.of(analysis.locationText())
			));
		}
		return proposals.stream().limit(10).toList();
	}

	private static String safetyRisk(ComplaintAnalysisResponse analysis) {
		return "EMERGENCY".equals(analysis.urgency()) || "HIGH".equals(analysis.urgency()) ? "HIGH" : "NORMAL";
	}

	private static String text(JsonNode node, String field, String fallback) {
		String value = node.path(field).asText();
		return value == null || value.isBlank() ? fallback : value;
	}

	private static List<String> values(JsonNode node, String fallback) {
		List<String> values = new ArrayList<>();
		if (node.isArray()) {
			node.forEach(value -> {
				if (value.isTextual() && !value.asText().isBlank()) {
					values.add(value.asText());
				}
			});
		}
		if (values.isEmpty() && fallback != null && !fallback.isBlank()) {
			values.add(fallback);
		}
		return values;
	}

	private static ComplaintType complaintType(String value) {
		try {
			return ComplaintType.valueOf(value);
		}
		catch (Exception exception) {
			return ComplaintType.GENERAL;
		}
	}

	private static boolean allowedDepartment(String code) {
		return code != null && (code.startsWith("SYNTHETIC_DEMO_")
				|| Set.of(
						"SAFETY_CONTROL", "RESOURCE_RECYCLING", "ROAD", "TRAFFIC", "CIVIL_AFFAIRS",
						"ENVIRONMENT", "BUILDING_HOUSING", "PARK_GREEN", "WATER_SEWER",
						"HEALTH_SANITATION", "ANIMAL_LIVESTOCK", "URBAN_MANAGEMENT", "WELFARE"
				).contains(code));
	}

	private String findSyntheticAssignmentDepartment(ComplaintType complaintType) {
		String organizationCode = jdbcTemplate.query("""
				select u.code
				from assignment_rules r
				join organization_units u on u.id = r.organization_unit_id
				where r.complaint_type = ?
				  and r.jurisdiction_code = 'SYNTHETIC_DEMO_PILOT'
				  and r.synthetic_demo = true
				  and r.active = true
				  and u.synthetic_demo = true
				  and u.active = true
				  and (r.valid_from is null or r.valid_from <= current_date)
				  and (r.valid_to is null or r.valid_to >= current_date)
				  and (u.valid_from is null or u.valid_from <= current_date)
				  and (u.valid_to is null or u.valid_to >= current_date)
				order by r.priority desc
				limit 1
				""",
				resultSet -> resultSet.next() ? resultSet.getString(1) : null,
				complaintType.name()
		);
		return departmentCodeFromOrganizationCode(organizationCode);
	}

	private String departmentCodeFromOrganizationCode(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return switch (value) {
			case "SYNTHETIC_DEMO_RESOURCE_RECYCLING" -> "RESOURCE_RECYCLING";
			case "SYNTHETIC_DEMO_ROAD" -> "ROAD";
			case "SYNTHETIC_DEMO_TRAFFIC" -> "TRAFFIC";
			default -> value;
		};
	}

	private record IssueProposal(
			String summary,
			ComplaintType complaintType,
			String jurisdictionStatus,
			String safetyRisk,
			String expressionRisk,
			String processability,
			List<String> departmentCandidates,
			List<String> locationCandidates
	) {
	}

	private String processDraft(ProcessingJob job) {
		String beforeComplaint = complaintState(complaintRepository.findById(job.getComplaintId())
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + job.getComplaintId())));
		Map<String, EvidenceSnapshot> snapshotsBySourceId = new HashMap<>();
		DraftResponse response = complaintService.generateDraft(
				job.getComplaintId(),
				documents -> snapshotsBySourceId.putAll(captureDraftEvidence(job, documents))
		);
		return completeDraft(job, response, beforeComplaint, snapshotsBySourceId);
	}

	private String applyWorkerDraft(
			ProcessingJob job,
			JsonNode output,
			List<Long> evidenceDocumentIds,
			String workerModelName
	) {
		String beforeComplaint = complaintState(complaintRepository.findById(job.getComplaintId())
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + job.getComplaintId())));
		Map<String, EvidenceSnapshot> snapshotsBySourceId = new HashMap<>();
		DraftResponse response = complaintService.applyWorkerDraft(
				job.getComplaintId(),
				output,
				evidenceDocumentIds,
				workerModelName,
				documents -> snapshotsBySourceId.putAll(captureDraftEvidence(job, documents))
		);
		return completeDraft(job, response, beforeComplaint, snapshotsBySourceId);
	}

	private String completeDraft(
			ProcessingJob job,
			DraftResponse response,
			String beforeComplaint,
			Map<String, EvidenceSnapshot> snapshotsBySourceId
	) {
		DraftFinalization result = transactionTemplate.execute(status -> {
			try {
				return new DraftFinalization(finalizeDraft(job, response, beforeComplaint, snapshotsBySourceId), null);
			}
			catch (WorkflowGateException exception) {
				return new DraftFinalization(null, exception);
			}
		});
		if (result.gateException() != null) {
			throw result.gateException();
		}
		return result.resultReference();
	}

	private Map<String, EvidenceSnapshot> captureDraftEvidence(ProcessingJob job, List<egovframework.example.complaint.domain.KnowledgeDocument> documents) {
		return transactionTemplate.execute(status -> {
			UUID complaintId = job.getComplaintId();
			LocalDate today = LocalDate.now();
			Set<String> conflictingLegalBases = conflictingLegalBases(documents, today);
			RetrievalRun retrievalRun = retrievalRunRepository.save(new RetrievalRun(
					complaintId,
					job.getId(),
					"draft evidence candidates for complaint " + complaintId,
					"DRAFT_EVIDENCE_REVIEW",
					"{\"capturesRejectedCandidates\":true,\"effectiveOn\":\"today\"}"
			));
			Map<String, EvidenceSnapshot> snapshots = new HashMap<>();
			for (egovframework.example.complaint.domain.KnowledgeDocument document : documents) {
				String legalBasis = normalizedLegalBasis(document);
				boolean supportsClaim = isSupportableOfficialEvidence(document, today)
						&& !conflictingLegalBases.contains(legalBasis);
				EvidenceSnapshot snapshot = evidenceSnapshotRepository.save(new EvidenceSnapshot(
						complaintId,
						retrievalRun.getId(),
						document,
						hashService.sha256(document.getContent()),
						supportsClaim
				));
				snapshots.put(snapshot.getSourceId(), snapshot);
			}
			long supportableCount = snapshots.values().stream().filter(EvidenceSnapshot::isSupportsClaim).count();
			audit("RETRIEVAL_RUN", retrievalRun.getId().toString(), "EVIDENCE_CANDIDATES_CAPTURED",
					null,
					"{\"candidateCount\":" + snapshots.size() + ",\"supportableCount\":" + supportableCount + "}",
					job.getIdempotencyKey());
			return snapshots;
		});
	}

	private String finalizeDraft(
			ProcessingJob job,
			DraftResponse response,
			String beforeComplaint,
			Map<String, EvidenceSnapshot> snapshotsBySourceId
	) {
		UUID complaintId = job.getComplaintId();
		OfficialDraft draft = officialDraftRepository.findById(response.draftId())
				.orElseThrow(() -> new EntityNotFoundException("Draft not found: " + response.draftId()));
		List<RagContext> contexts = ragContextRepository.findByOfficialDraftIdOrderByIdAsc(draft.getId());
		if (verificationResultRepository.existsByOfficialDraftIdAndRuleCodeAndStatus(
				draft.getId(), "CLAIM_EVIDENCE_COVERAGE", "PASSED"
		)) {
			return String.valueOf(draft.getId());
		}
		List<DraftClaim> claims = draftClaimRepository.findByOfficialDraft_IdOrderByClaimIndexAsc(draft.getId());
		if (claims.isEmpty()) {
			recordVerification(complaintId, draft.getId(), "DRAFT_CLAIMS_SCHEMA", false,
					"A draft must contain validated structured claims");
			rejectDraft(complaintId, draft, WorkflowBlocker.EVIDENCE_INSUFFICIENT);
		}
		Set<String> contextSourceIds = contexts.stream()
				.map(context -> String.valueOf(context.getKnowledgeDocument().getId()))
				.collect(java.util.stream.Collectors.toSet());
		boolean claimSourcesValid = claims.stream()
				.map(DraftClaim::sourceDocumentIds)
				.allMatch(sourceIds -> !sourceIds.isEmpty() && contextSourceIds.containsAll(sourceIds));
		recordVerification(complaintId, draft.getId(), "DRAFT_CLAIMS_SCHEMA", claimSourcesValid,
				"Every structured claim must reference only supplied evidence IDs");
		if (!claimSourcesValid) {
			rejectDraft(complaintId, draft, WorkflowBlocker.EVIDENCE_INSUFFICIENT);
		}
		verifyDraftBeforeUse(complaintId, draft, contexts);
		boolean snapshotsComplete = contextSourceIds.stream()
				.map(snapshotsBySourceId::get)
				.allMatch(snapshot -> snapshot != null && snapshot.isSupportsClaim());
		recordVerification(complaintId, draft.getId(), "IMMUTABLE_EVIDENCE_SNAPSHOT", snapshotsComplete,
				"Every selected source must have an immutable, supportable evidence snapshot");
		if (!snapshotsComplete) {
			rejectDraft(complaintId, draft, WorkflowBlocker.EVIDENCE_INSUFFICIENT);
		}
		for (DraftClaim claim : claims) {
			Set<String> sourceIds = claim.sourceDocumentIds();
			for (String sourceId : sourceIds) {
				EvidenceSnapshot snapshot = snapshotsBySourceId.get(sourceId);
				jdbcTemplate.update(
						"insert into claim_evidence_links (draft_claim_id, evidence_snapshot_id, relation_type, created_at) values (?, ?, ?, ?)",
						claim.getId(), snapshot.getId(), "SUPPORTS", LocalDateTime.now()
				);
			}
		}
		verificationResultRepository.save(new VerificationResult(
				complaintId, draft.getId(), "CLAIM_EVIDENCE_COVERAGE", "PASSED",
				"All generated claims are linked to immutable evidence snapshots", false
		));
		Complaint complaint = complaintRepository.findById(complaintId)
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + complaintId));
		auditIfChanged("COMPLAINT", complaintId.toString(), "DRAFT_VERIFIED",
				beforeComplaint, complaintState(complaint), job.getIdempotencyKey());
		audit("OFFICIAL_DRAFT", draft.getId().toString(), "DRAFT_VERIFIED",
				null, draftState(draft), job.getIdempotencyKey());
		return String.valueOf(draft.getId());
	}

	private void verifyDraftBeforeUse(UUID complaintId, OfficialDraft draft, List<RagContext> contexts) {
		LocalDate today = LocalDate.now();
		boolean officialEvidence = contexts.stream()
				.anyMatch(context -> context.getKnowledgeDocument().isOfficialLegalEvidence(today));
		boolean eligibleEvidence = !contexts.isEmpty() && contexts.stream()
				.allMatch(context -> context.getKnowledgeDocument().isEligibleEvidence(today));
		boolean jurisdictionValid = contexts.stream()
				.filter(context -> context.getKnowledgeDocument().isOfficialLegalEvidence(today))
				.allMatch(context -> {
					String jurisdiction = context.getKnowledgeDocument().getJurisdictionCode();
					return jurisdiction != null && jurisdiction.equalsIgnoreCase("NATIONAL");
				});
		boolean sourceMetadataComplete = contexts.stream()
				.filter(context -> context.getKnowledgeDocument().isOfficialLegalEvidence(today))
				.allMatch(context -> context.getKnowledgeDocument().getLegalBasis() != null
						&& !context.getKnowledgeDocument().getLegalBasis().isBlank()
						&& context.getKnowledgeDocument().getSourceUrl() != null
						&& !context.getKnowledgeDocument().getSourceUrl().isBlank()
						&& context.getKnowledgeDocument().getSourceVersion() != null
						&& !context.getKnowledgeDocument().getSourceVersion().isBlank()
						&& context.getKnowledgeDocument().getContentHash() != null
						&& !context.getKnowledgeDocument().getContentHash().isBlank()
						&& context.getKnowledgeDocument().getContentHash()
								.equals(hashService.sha256(context.getKnowledgeDocument().getContent())));
		boolean conflictFree = hasNoOverlappingOfficialConflicts(contexts, today);
		boolean piiSafe = !redactionService.containsSensitivePattern(draft.getDraftText());

		recordVerification(complaintId, draft.getId(), "OFFICIAL_EVIDENCE_REQUIRED", officialEvidence,
				"At least one verified official legal source is required");
		recordVerification(complaintId, draft.getId(), "SOURCE_EFFECTIVE_STATUS", eligibleEvidence,
				"Every source must be verified and effective on the processing date");
		recordVerification(complaintId, draft.getId(), "JURISDICTION_FILTER", jurisdictionValid,
				"Legal claims in the synthetic pilot require official national-law evidence");
		recordVerification(complaintId, draft.getId(), "REQUIRED_SOURCE_METADATA", sourceMetadataComplete,
				"Official legal sources require a source URL, legal basis, source version, and content hash");
		recordVerification(complaintId, draft.getId(), "CONFLICT_SCAN", conflictFree,
				"Overlapping official sources for the same legal basis must contain consistent content");
		recordVerification(complaintId, draft.getId(), "PII_OUTPUT_CHECK", piiSafe,
				"Draft output must not contain recognizable PII patterns");

		if (!officialEvidence || !eligibleEvidence || !sourceMetadataComplete) {
			rejectDraft(complaintId, draft, WorkflowBlocker.EVIDENCE_INSUFFICIENT);
		}
		if (!jurisdictionValid) {
			rejectDraft(complaintId, draft, WorkflowBlocker.NEEDS_JURISDICTION);
		}
		if (!conflictFree) {
			rejectDraft(complaintId, draft, WorkflowBlocker.CONFLICT_DETECTED);
		}
		if (!piiSafe) {
			rejectDraft(complaintId, draft, WorkflowBlocker.PROCESSING_FAILED);
		}
	}

	private boolean hasNoOverlappingOfficialConflicts(List<RagContext> contexts, LocalDate today) {
		Map<String, Set<String>> hashesByLegalBasis = new HashMap<>();
		for (RagContext context : contexts) {
			if (!context.getKnowledgeDocument().isOfficialLegalEvidence(today)) {
				continue;
			}
			String legalBasis = context.getKnowledgeDocument().getLegalBasis();
			if (legalBasis == null || legalBasis.isBlank()) {
				continue;
			}
			hashesByLegalBasis.computeIfAbsent(legalBasis.trim(), ignored -> new HashSet<>())
					.add(hashService.sha256(context.getKnowledgeDocument().getContent()));
		}
		return hashesByLegalBasis.values().stream().allMatch(hashes -> hashes.size() <= 1);
	}

	private Set<String> conflictingLegalBases(
			List<egovframework.example.complaint.domain.KnowledgeDocument> documents,
			LocalDate today
	) {
		Map<String, Set<String>> hashesByLegalBasis = new HashMap<>();
		for (egovframework.example.complaint.domain.KnowledgeDocument document : documents) {
			if (!isSupportableOfficialEvidence(document, today)) {
				continue;
			}
			hashesByLegalBasis.computeIfAbsent(normalizedLegalBasis(document), ignored -> new HashSet<>())
					.add(hashService.sha256(document.getContent()));
		}
		return hashesByLegalBasis.entrySet().stream()
				.filter(entry -> entry.getValue().size() > 1)
				.map(Map.Entry::getKey)
				.collect(java.util.stream.Collectors.toSet());
	}

	private boolean isSupportableOfficialEvidence(
			egovframework.example.complaint.domain.KnowledgeDocument document,
			LocalDate today
	) {
		return document.isOfficialLegalEvidence(today)
				&& document.getLegalBasis() != null
				&& !document.getLegalBasis().isBlank()
				&& document.getSourceUrl() != null
				&& !document.getSourceUrl().isBlank()
				&& document.getSourceVersion() != null
				&& !document.getSourceVersion().isBlank()
				&& document.getContentHash() != null
				&& document.getContentHash().equals(hashService.sha256(document.getContent()));
	}

	private String normalizedLegalBasis(egovframework.example.complaint.domain.KnowledgeDocument document) {
		return document.getLegalBasis() == null ? "" : document.getLegalBasis().trim();
	}

	private void recordVerification(UUID complaintId, Long draftId, String rule, boolean passed, String message) {
		verificationResultRepository.save(new VerificationResult(
				complaintId,
				draftId,
				rule,
				passed ? "PASSED" : "FAILED",
				message,
				!passed
		));
	}

	private void rejectDraft(UUID complaintId, OfficialDraft draft, WorkflowBlocker blocker) {
		String beforeDraft = draftState(draft);
		draft.rejectForVerification("Deterministic verification hard gate failed: " + blocker);
		officialDraftRepository.saveAndFlush(draft);
		Complaint complaint = complaintRepository.findById(complaintId)
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + complaintId));
		String beforeComplaint = complaintState(complaint);
		complaint.block(blocker);
		complaintRepository.saveAndFlush(complaint);
		audit("OFFICIAL_DRAFT", draft.getId().toString(), "DETERMINISTIC_VERIFICATION_REJECTED",
				beforeDraft, draftState(draft), null);
		audit("COMPLAINT", complaintId.toString(), "DETERMINISTIC_VERIFICATION_BLOCKED",
				beforeComplaint, complaintState(complaint), null);
		throw new WorkflowGateException(blocker, "Deterministic verification hard gate failed: " + blocker);
	}

	protected ProcessingJob start(UUID jobId) {
		return transactionTemplate.execute(status -> {
			ProcessingJob job = processingJobRepository.findByIdForUpdate(jobId)
					.orElseThrow(() -> new EntityNotFoundException("Processing job not found: " + jobId));
			String before = jobState(job);
			job.start(LocalDateTime.now().plusMinutes(5));
			ProcessingJob saved = processingJobRepository.saveAndFlush(job);
			audit("PROCESSING_JOB", jobId.toString(), "START_" + job.getJobType(),
					before, jobState(saved), saved.getIdempotencyKey());
			return saved;
		});
	}

	protected void succeed(UUID jobId, String resultReference) {
		transactionTemplate.executeWithoutResult(status -> {
			ProcessingJob job = processingJobRepository.findByIdForUpdate(jobId)
					.orElseThrow(() -> new EntityNotFoundException("Processing job not found: " + jobId));
			String before = jobState(job);
			job.succeed(resultReference);
			ProcessingJob saved = processingJobRepository.saveAndFlush(job);
			audit("PROCESSING_JOB", jobId.toString(), "SUCCEED_" + job.getJobType(),
					before, jobState(saved), saved.getIdempotencyKey());
		});
	}

	protected void fail(UUID jobId, String reason, boolean blocked, WorkflowBlocker blocker) {
		transactionTemplate.executeWithoutResult(status -> {
			ProcessingJob job = processingJobRepository.findByIdForUpdate(jobId)
					.orElseThrow(() -> new EntityNotFoundException("Processing job not found: " + jobId));
			String before = jobState(job);
			job.fail(reason == null ? "Unknown processing failure" : reason, blocked);
			ProcessingJob saved = processingJobRepository.saveAndFlush(job);
			audit("PROCESSING_JOB", jobId.toString(), (blocked ? "BLOCK_" : "FAIL_") + job.getJobType(),
					before, jobState(saved), saved.getIdempotencyKey());
			if (blocker != null) {
				Complaint complaint = complaintRepository.findById(job.getComplaintId())
						.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + job.getComplaintId()));
				String beforeComplaint = complaintState(complaint);
				complaint.block(blocker);
				complaintRepository.saveAndFlush(complaint);
				audit("COMPLAINT", complaint.getId().toString(), "PROCESSING_BLOCKED",
						beforeComplaint, complaintState(complaint), saved.getIdempotencyKey());
				verificationResultRepository.save(new VerificationResult(
						complaint.getId(),
						null,
						"PROCESSING_JOB_FAILURE",
						"FAILED",
						reason == null ? "Unknown processing failure" : reason,
						true
				));
			}
		});
	}

	private void auditIfChanged(String entityType, String entityId, String action, String before, String after,
			String idempotencyKey) {
		if (!before.equals(after)) {
			audit(entityType, entityId, action, before, after, idempotencyKey);
		}
	}

	private void audit(String entityType, String entityId, String action, String before, String after,
			String idempotencyKey) {
		workflowAuditEventRepository.save(new WorkflowAuditEvent(
				entityType,
				entityId,
				action,
				"system-job-runner",
				"SYSTEM_WORKER",
				before,
				after,
				idempotencyKey
		));
	}

	private String complaintState(Complaint complaint) {
		return "{\"status\":\"" + complaint.getStatus() + "\",\"blocker\":\""
				+ (complaint.getWorkflowBlocker() == null ? "" : complaint.getWorkflowBlocker())
				+ "\",\"version\":" + complaint.getVersion() + "}";
	}

	private String draftState(OfficialDraft draft) {
		return "{\"status\":\"" + draft.getStatus() + "\",\"version\":" + draft.getVersion() + "}";
	}

	private String jobState(ProcessingJob job) {
		return "{\"status\":\"" + job.getStatus() + "\",\"attempts\":" + job.getAttempts()
				+ ",\"version\":" + job.getVersion() + "}";
	}

	private static final class WorkflowGateException extends RuntimeException {

		private final WorkflowBlocker blocker;

		private WorkflowGateException(WorkflowBlocker blocker, String message) {
			super(message);
			this.blocker = blocker;
		}

		private WorkflowBlocker blocker() {
			return blocker;
		}
	}

	private record DraftFinalization(String resultReference, WorkflowGateException gateException) {
	}
}
