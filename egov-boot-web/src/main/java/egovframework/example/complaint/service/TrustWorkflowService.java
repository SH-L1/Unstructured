package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.AttachmentResponse;
import egovframework.example.complaint.api.dto.AiRunResponse;
import egovframework.example.complaint.api.dto.ComplaintIssueResponse;
import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.api.dto.EvidenceSnapshotResponse;
import egovframework.example.complaint.api.dto.DraftClaimResponse;
import egovframework.example.complaint.api.dto.HumanReviewResponse;
import egovframework.example.complaint.api.dto.ProcessingJobResponse;
import egovframework.example.complaint.api.dto.TrustComplaintDetailResponse;
import egovframework.example.complaint.api.dto.TrustDraftResponse;
import egovframework.example.complaint.api.dto.VerificationResultResponse;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintIssue;
import egovframework.example.complaint.domain.ComplaintStatus;
import egovframework.example.complaint.domain.HumanReview;
import egovframework.example.complaint.domain.IdempotencyRecord;
import egovframework.example.complaint.domain.LocationCandidate;
import egovframework.example.complaint.domain.OfficialDraft;
import egovframework.example.complaint.domain.ProcessingJob;
import egovframework.example.complaint.domain.ProcessingJobType;
import egovframework.example.complaint.domain.WorkflowAuditEvent;
import egovframework.example.complaint.domain.WorkflowBlocker;
import egovframework.example.complaint.repository.ComplaintIssueRepository;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.AiRunRepository;
import egovframework.example.complaint.repository.AttachmentAnalysisRepository;
import egovframework.example.complaint.repository.DepartmentTaskRepository;
import egovframework.example.complaint.repository.DraftClaimRepository;
import egovframework.example.complaint.repository.EvidenceSnapshotRepository;
import egovframework.example.complaint.repository.HumanReviewRepository;
import egovframework.example.complaint.repository.IdempotencyRecordRepository;
import egovframework.example.complaint.repository.LocationCandidateRepository;
import egovframework.example.complaint.repository.OfficialDraftRepository;
import egovframework.example.complaint.repository.ProcessingJobRepository;
import egovframework.example.complaint.repository.RetrievalRunRepository;
import egovframework.example.complaint.repository.WorkflowAuditEventRepository;
import egovframework.example.complaint.repository.VerificationResultRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TrustWorkflowService {

	private final ComplaintService complaintService;
	private final ComplaintRepository complaintRepository;
	private final ComplaintIssueRepository complaintIssueRepository;
	private final DepartmentTaskRepository departmentTaskRepository;
	private final DraftClaimRepository draftClaimRepository;
	private final LocationCandidateRepository locationCandidateRepository;
	private final OfficialDraftRepository officialDraftRepository;
	private final ProcessingJobRepository processingJobRepository;
	private final HumanReviewRepository humanReviewRepository;
	private final IdempotencyRecordRepository idempotencyRecordRepository;
	private final ProcessingJobRunner processingJobRunner;
	private final CurrentActorService currentActorService;
	private final WorkflowAuditEventRepository workflowAuditEventRepository;
	private final VerificationResultRepository verificationResultRepository;
	private final AiRunRepository aiRunRepository;
	private final EvidenceSnapshotRepository evidenceSnapshotRepository;
	private final RetrievalRunRepository retrievalRunRepository;
	private final RedactionService redactionService;
	private final AttachmentAnalysisRepository attachmentAnalysisRepository;
	private final boolean embeddedJobExecution;

	public TrustWorkflowService(
			ComplaintService complaintService,
			ComplaintRepository complaintRepository,
			ComplaintIssueRepository complaintIssueRepository,
			DepartmentTaskRepository departmentTaskRepository,
			DraftClaimRepository draftClaimRepository,
			LocationCandidateRepository locationCandidateRepository,
			OfficialDraftRepository officialDraftRepository,
			ProcessingJobRepository processingJobRepository,
			HumanReviewRepository humanReviewRepository,
			IdempotencyRecordRepository idempotencyRecordRepository,
			ProcessingJobRunner processingJobRunner,
			CurrentActorService currentActorService,
			WorkflowAuditEventRepository workflowAuditEventRepository,
			VerificationResultRepository verificationResultRepository,
			AiRunRepository aiRunRepository,
			EvidenceSnapshotRepository evidenceSnapshotRepository,
			RetrievalRunRepository retrievalRunRepository,
			RedactionService redactionService,
			AttachmentAnalysisRepository attachmentAnalysisRepository,
			@Value("${app.jobs.execution-mode:python}") String jobExecutionMode
	) {
		this.complaintService = complaintService;
		this.complaintRepository = complaintRepository;
		this.complaintIssueRepository = complaintIssueRepository;
		this.departmentTaskRepository = departmentTaskRepository;
		this.draftClaimRepository = draftClaimRepository;
		this.locationCandidateRepository = locationCandidateRepository;
		this.officialDraftRepository = officialDraftRepository;
		this.processingJobRepository = processingJobRepository;
		this.humanReviewRepository = humanReviewRepository;
		this.idempotencyRecordRepository = idempotencyRecordRepository;
		this.processingJobRunner = processingJobRunner;
		this.currentActorService = currentActorService;
		this.workflowAuditEventRepository = workflowAuditEventRepository;
		this.verificationResultRepository = verificationResultRepository;
		this.aiRunRepository = aiRunRepository;
		this.evidenceSnapshotRepository = evidenceSnapshotRepository;
		this.retrievalRunRepository = retrievalRunRepository;
		this.redactionService = redactionService;
		this.attachmentAnalysisRepository = attachmentAnalysisRepository;
		this.embeddedJobExecution = "embedded".equalsIgnoreCase(jobExecutionMode);
	}

	@Transactional
	public ComplaintResponse create(CreateComplaintRequest request, String idempotencyKey) {
		String key = requireIdempotencyKey(idempotencyKey);
		IdempotencyRecord existing = idempotencyRecordRepository
				.findByOperationAndIdempotencyKey("CREATE_COMPLAINT", key)
				.orElse(null);
		if (existing != null) {
			requireCompleted(existing);
			return complaintService.findById(UUID.fromString(existing.getResourceId()));
		}
		IdempotencyRecord reservation = idempotencyRecordRepository.saveAndFlush(
				IdempotencyRecord.pending("CREATE_COMPLAINT", key, null)
		);
		ComplaintResponse created = complaintService.create(request);
		reservation.complete(created.id().toString());
		idempotencyRecordRepository.saveAndFlush(reservation);
		audit("COMPLAINT", created.id().toString(), "CREATE", "INTAKE", null,
				complaintState(created), key);
		return created;
	}

	@Transactional
	public AttachmentResponse addAttachment(
			UUID complaintId,
			MultipartFile file,
			String idempotencyKey,
			long expectedVersion
	) {
		String key = requireIdempotencyKey(idempotencyKey);
		IdempotencyRecord existing = idempotencyRecordRepository
				.findByOperationAndIdempotencyKey("ADD_ATTACHMENT", key)
				.orElse(null);
		if (existing != null) {
			requireCompleted(existing);
			String prefix = complaintId + ":";
			String resourceId = existing.getResourceId();
			if (resourceId.contains(":") && !resourceId.startsWith(prefix)) {
				throw new IllegalStateException("Idempotency-Key was already used for another complaint attachment");
			}
			UUID attachmentId = UUID.fromString(
					resourceId.startsWith(prefix) ? resourceId.substring(prefix.length()) : resourceId
			);
			return complaintService.findAttachments(complaintId).stream()
					.filter(attachment -> attachment.id().equals(attachmentId))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("Idempotent attachment result no longer exists"));
		}
		Complaint complaint = getComplaint(complaintId);
		requireVersion(expectedVersion, complaint.getVersion());
		IdempotencyRecord reservation = idempotencyRecordRepository.saveAndFlush(
				IdempotencyRecord.pending("ADD_ATTACHMENT", key, complaintId.toString())
		);
		AttachmentResponse response = complaintService.addAttachment(complaintId, file);
		reservation.complete(complaintId + ":" + response.id());
		idempotencyRecordRepository.saveAndFlush(reservation);
		ProcessingJob extractionJob = processingJobRepository.save(new ProcessingJob(
				complaint,
				ProcessingJobType.EXTRACT_ATTACHMENT,
				"attachment-extract:" + response.id(),
				3,
				response.id().toString()
		));
		audit("ATTACHMENT", response.id().toString(), "ADD", "INTAKE", null,
				"{\"complaintId\":\"" + complaintId + "\",\"quarantine\":\"PENDING_SCAN\"}", key);
		audit("PROCESSING_JOB", extractionJob.getId().toString(), "ENQUEUE_EXTRACT_ATTACHMENT", "INTAKE", null,
				"{\"status\":\"" + extractionJob.getStatus() + "\",\"attachmentId\":\"" + response.id() + "\"}", key);
		return response;
	}

	@Transactional
	public void deleteAttachment(UUID complaintId, UUID attachmentId, String idempotencyKey, long expectedVersion) {
		String key = requireIdempotencyKey(idempotencyKey);
		String resourceId = complaintId + ":" + attachmentId;
		IdempotencyRecord existing = idempotencyRecordRepository
				.findByOperationAndIdempotencyKey("DELETE_ATTACHMENT", key)
				.orElse(null);
		if (existing != null) {
			requireCompleted(existing);
			if (!existing.getResourceId().equals(resourceId)) {
				throw new IllegalStateException("Idempotency-Key was already used for another attachment deletion");
			}
			return;
		}
		Complaint complaint = getComplaint(complaintId);
		requireVersion(expectedVersion, complaint.getVersion());
		IdempotencyRecord reservation = idempotencyRecordRepository.saveAndFlush(
				IdempotencyRecord.pending("DELETE_ATTACHMENT", key, resourceId)
		);
		complaintService.deleteAttachment(complaintId, attachmentId);
		reservation.complete(resourceId);
		idempotencyRecordRepository.saveAndFlush(reservation);
		audit("ATTACHMENT", attachmentId.toString(), "DELETE", "INTAKE",
				"{\"complaintId\":\"" + complaintId + "\"}", "{\"deleted\":true}", key);
	}

	@Transactional(readOnly = true)
	public List<AttachmentResponse> findAttachments(UUID complaintId) {
		return complaintService.findAttachments(complaintId);
	}

	@Transactional
	public ProcessingJobResponse enqueueAnalysis(UUID complaintId, String idempotencyKey, long expectedVersion) {
		String key = requireIdempotencyKey(idempotencyKey);
		ProcessingJob existing = processingJobRepository
				.findByJobTypeAndIdempotencyKey(ProcessingJobType.CLASSIFY_ISSUES, key)
				.orElse(null);
		if (existing != null) {
			if (!existing.getComplaintId().equals(complaintId)) {
				throw new IllegalStateException("Idempotency-Key was already used for another complaint");
			}
			return ProcessingJobResponse.from(existing);
		}
		Complaint complaint = getComplaint(complaintId);
		requireVersion(expectedVersion, complaint.getVersion());
		if (complaint.getStatus() != ComplaintStatus.RECEIVED
				&& complaint.getStatus() != ComplaintStatus.TRIAGE_REVIEW) {
			throw new IllegalStateException("Analysis can only be requested before draft review begins");
		}
		requireApprovedAttachmentDerivatives(complaintId);
		return enqueue(complaintId, ProcessingJobType.CLASSIFY_ISSUES, key, expectedVersion);
	}

	@Transactional
	public ProcessingJobResponse enqueueDraft(UUID complaintId, String idempotencyKey, long expectedVersion) {
		String key = requireIdempotencyKey(idempotencyKey);
		ProcessingJob existing = processingJobRepository
				.findByJobTypeAndIdempotencyKey(ProcessingJobType.DRAFT, key)
				.orElse(null);
		if (existing != null) {
			if (!existing.getComplaintId().equals(complaintId)) {
				throw new IllegalStateException("Idempotency-Key was already used for another complaint");
			}
			return ProcessingJobResponse.from(existing);
		}
		Complaint complaint = getComplaint(complaintId);
		requireVersion(expectedVersion, complaint.getVersion());
		if (complaint.getStatus() != ComplaintStatus.TRIAGE_REVIEW
				&& complaint.getStatus() != ComplaintStatus.DRAFT_REVIEW) {
			throw new IllegalStateException("Complaint must be in TRIAGE_REVIEW or rejected DRAFT_REVIEW before draft generation");
		}
		if (complaint.getStatus() == ComplaintStatus.DRAFT_REVIEW) {
			OfficialDraft latest = officialDraftRepository.findByComplaintIdOrderByCreatedAtDesc(complaintId).stream()
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("DRAFT_REVIEW requires an existing rejected draft"));
			if (latest.getStatus() != egovframework.example.complaint.domain.DraftStatus.REJECTED) {
				throw new IllegalStateException("A new draft can only be requested after the previous draft was rejected");
			}
		}
		if (complaint.getWorkflowBlocker() == WorkflowBlocker.NEEDS_LOCATION
				|| complaint.getWorkflowBlocker() == WorkflowBlocker.NEEDS_JURISDICTION) {
			throw new IllegalStateException("Complaint is blocked: " + complaint.getWorkflowBlocker());
		}
		requireApprovedAttachmentDerivatives(complaintId);
		return enqueue(complaintId, ProcessingJobType.DRAFT, key, expectedVersion);
	}

	private ProcessingJobResponse enqueue(UUID complaintId, ProcessingJobType type, String idempotencyKey, long expectedVersion) {
		String key = requireIdempotencyKey(idempotencyKey);
		ProcessingJob existing = processingJobRepository.findByJobTypeAndIdempotencyKey(type, key).orElse(null);
		if (existing != null) {
			if (!existing.getComplaintId().equals(complaintId)) {
				throw new IllegalStateException("Idempotency-Key was already used for another complaint");
			}
			return ProcessingJobResponse.from(existing);
		}
		Complaint complaint = getComplaint(complaintId);
		requireVersion(expectedVersion, complaint.getVersion());
		ProcessingJob job = processingJobRepository.saveAndFlush(new ProcessingJob(complaint, type, key, 3));
		audit("PROCESSING_JOB", job.getId().toString(), "ENQUEUE_" + type.name(), "REVIEWER", null,
				"{\"status\":\"" + job.getStatus() + "\",\"complaintId\":\"" + complaintId + "\"}", key);
		if (embeddedJobExecution) {
			runAfterCommit(job.getId());
		}
		return ProcessingJobResponse.from(job);
	}

	@Transactional(readOnly = true)
	public ProcessingJobResponse findJob(UUID jobId) {
		return ProcessingJobResponse.from(processingJobRepository.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("Processing job not found: " + jobId)));
	}

	@Transactional(readOnly = true)
	public TrustComplaintDetailResponse detail(UUID complaintId) {
		ComplaintResponse complaint = complaintService.findById(complaintId);
		ComplaintAnalysisResponse analysis = complaintService.findAnalysis(complaintId);
		List<ComplaintIssueResponse> issues = complaintIssueRepository.findByComplaint_IdOrderByIssueIndexAsc(complaintId).stream()
				.map(issue -> ComplaintIssueResponse.from(
						issue,
						departmentTaskRepository.findByIssue_IdOrderByCreatedAtAsc(issue.getId()).stream()
								.map(egovframework.example.complaint.domain.DepartmentTask::getDepartmentCode)
								.toList(),
						locationCandidateRepository.findByIssue_IdOrderByCreatedAtAsc(issue.getId()).stream()
								.map(egovframework.example.complaint.domain.LocationCandidate::getLocationText)
								.toList()
				))
				.toList();
		OfficialDraft latestDraft = officialDraftRepository.findByComplaintIdOrderByCreatedAtDesc(complaintId).stream()
				.findFirst()
				.orElse(null);
		TrustDraftResponse draft = latestDraft == null ? null : TrustDraftResponse.from(latestDraft);
		return new TrustComplaintDetailResponse(
				complaint,
				analysis,
				issues,
				draft,
				(latestDraft == null ? List.<egovframework.example.complaint.domain.DraftClaim>of()
						: draftClaimRepository.findByOfficialDraft_IdOrderByClaimIndexAsc(latestDraft.getId())).stream()
						.map(DraftClaimResponse::from)
						.toList(),
				retrievalRunRepository.findFirstByComplaintIdOrderByCreatedAtDesc(complaintId)
						.map(run -> evidenceSnapshotRepository.findByRetrievalRunIdOrderByCreatedAtDesc(run.getId()))
						.orElseGet(List::of).stream()
						.map(EvidenceSnapshotResponse::from)
						.toList(),
				verificationResultRepository.findByComplaintIdOrderByCreatedAtDesc(complaintId).stream()
						.map(VerificationResultResponse::from)
						.toList(),
				aiRunRepository.findByComplaintIdOrderByCreatedAtDesc(complaintId).stream()
						.map(AiRunResponse::from)
						.toList(),
				humanReviewRepository.findByComplaintIdOrderByCreatedAtDesc(complaintId).stream()
						.map(HumanReviewResponse::from)
						.toList()
		);
	}

	@Transactional
	public ComplaintResponse confirmLocation(UUID issueId, String locationText, String idempotencyKey, long expectedVersion) {
		String key = requireIdempotencyKey(idempotencyKey);
		ComplaintIssue issue = complaintIssueRepository.findById(issueId)
				.orElseThrow(() -> new EntityNotFoundException("Complaint issue not found: " + issueId));
		Complaint complaint = getComplaint(issue.getComplaintId());
		IdempotencyRecord existing = idempotencyRecordRepository
				.findByOperationAndIdempotencyKey("CONFIRM_LOCATION", key)
				.orElse(null);
		if (existing != null) {
			requireCompleted(existing);
			if (!existing.getResourceId().equals(issueId.toString())) {
				throw new IllegalStateException("Idempotency-Key was already used for another location confirmation");
			}
			return complaintService.findById(complaint.getId());
		}
		requireVersion(expectedVersion, complaint.getVersion());
		IdempotencyRecord reservation = idempotencyRecordRepository.saveAndFlush(
				IdempotencyRecord.pending("CONFIRM_LOCATION", key, issueId.toString())
		);
		CurrentActorService.Actor actor = currentActorService.current("REVIEWER");
		String before = complaintState(ComplaintResponse.from(complaint));
		String redactedLocation = redactionService.redact(locationText);
		LocationCandidate candidate = new LocationCandidate(issue, redactedLocation, "HUMAN_CONFIRMED");
		candidate.confirm(actor.name());
		locationCandidateRepository.save(candidate);
		complaint.confirmLocation(redactedLocation);
		boolean jurisdictionUnresolved = complaintIssueRepository
				.findByComplaint_IdOrderByIssueIndexAsc(complaint.getId()).stream()
				.anyMatch(candidateIssue -> "NEEDS_JURISDICTION".equals(candidateIssue.getJurisdictionStatus()));
		if (jurisdictionUnresolved) {
			complaint.block(WorkflowBlocker.NEEDS_JURISDICTION);
		}
		complaintRepository.save(complaint);
		reservation.complete(issueId.toString());
		idempotencyRecordRepository.saveAndFlush(reservation);
		ComplaintResponse response = complaintService.findById(complaint.getId());
		audit("COMPLAINT", complaint.getId().toString(), "CONFIRM_LOCATION", actor, before, complaintState(response), key);
		return response;
	}

	@Transactional
	public TrustDraftResponse review(Long draftId, boolean approved, String notes, String idempotencyKey, long expectedVersion) {
		String key = requireIdempotencyKey(idempotencyKey);
		OfficialDraft draft = getDraft(draftId);
		HumanReview existingReview = humanReviewRepository.findByIdempotencyKey(key).orElse(null);
		if (existingReview != null) {
			requireSameHumanDecision(existingReview, draftId, "REVIEW_");
			return TrustDraftResponse.from(draft);
		}
		requireVersion(expectedVersion, draft.getVersion());
		CurrentActorService.Actor actor = currentActorService.current("REVIEWER");
		Complaint complaint = draft.getComplaint();
		String before = draftState(draft, complaint);
		if (approved) {
			draft.markReviewed(actor.name(), notes);
			complaint.markApprovalPending();
		}
		else {
			draft.rejectFromReview(notes);
			complaint.returnToDraftReview();
		}
		humanReviewRepository.save(new HumanReview(
				complaint.getId(), draft.getId(), approved ? "REVIEW_APPROVED" : "REVIEW_REJECTED",
				actor.name(), actor.role(), notes, key
		));
		complaintRepository.save(complaint);
		TrustDraftResponse response = TrustDraftResponse.from(officialDraftRepository.saveAndFlush(draft));
		audit("OFFICIAL_DRAFT", draft.getId().toString(), approved ? "REVIEW_APPROVED" : "REVIEW_REJECTED",
				actor, before, draftState(response, complaint), key);
		return response;
	}

	@Transactional
	public TrustDraftResponse approve(Long draftId, boolean approved, String notes, String idempotencyKey, long expectedVersion) {
		String key = requireIdempotencyKey(idempotencyKey);
		OfficialDraft draft = getDraft(draftId);
		HumanReview existingReview = humanReviewRepository.findByIdempotencyKey(key).orElse(null);
		if (existingReview != null) {
			requireSameHumanDecision(existingReview, draftId, "APPROV");
			return TrustDraftResponse.from(draft);
		}
		requireVersion(expectedVersion, draft.getVersion());
		CurrentActorService.Actor actor = currentActorService.current("APPROVER");
		Complaint complaint = draft.getComplaint();
		String before = draftState(draft, complaint);
		if (approved) {
			draft.approve(actor.name(), notes);
			complaint.markApproved();
		}
		else {
			draft.rejectFromApproval(actor.name(), notes);
			complaint.returnToDraftReview();
		}
		humanReviewRepository.save(new HumanReview(
				complaint.getId(), draft.getId(), approved ? "APPROVED" : "APPROVAL_REJECTED",
				actor.name(), actor.role(), notes, key
		));
		complaintRepository.save(complaint);
		TrustDraftResponse response = TrustDraftResponse.from(officialDraftRepository.saveAndFlush(draft));
		audit("OFFICIAL_DRAFT", draft.getId().toString(), approved ? "APPROVED" : "APPROVAL_REJECTED",
				actor, before, draftState(response, complaint), key);
		return response;
	}

	@Transactional
	public ComplaintResponse complete(UUID complaintId, String idempotencyKey, long expectedVersion) {
		String key = requireIdempotencyKey(idempotencyKey);
		IdempotencyRecord existing = idempotencyRecordRepository
				.findByOperationAndIdempotencyKey("COMPLETE_COMPLAINT", key)
				.orElse(null);
		if (existing != null) {
			requireCompleted(existing);
			if (!existing.getResourceId().equals(complaintId.toString())) {
				throw new IllegalStateException("Idempotency-Key was already used for another complaint completion");
			}
			return complaintService.findById(complaintId);
		}
		Complaint complaint = getComplaint(complaintId);
		requireVersion(expectedVersion, complaint.getVersion());
		IdempotencyRecord reservation = idempotencyRecordRepository.saveAndFlush(
				IdempotencyRecord.pending("COMPLETE_COMPLAINT", key, complaintId.toString())
		);
		String before = complaintState(ComplaintResponse.from(complaint));
		complaint.markCompleted();
		complaintRepository.saveAndFlush(complaint);
		reservation.complete(complaintId.toString());
		idempotencyRecordRepository.saveAndFlush(reservation);
		ComplaintResponse response = complaintService.findById(complaintId);
		audit("COMPLAINT", complaintId.toString(), "MANUAL_COMPLETE", "APPROVER", before, complaintState(response), key);
		return response;
	}

	private void audit(
			String entityType,
			String entityId,
			String action,
			String fallbackRole,
			String before,
			String after,
			String idempotencyKey
	) {
		audit(entityType, entityId, action, currentActorService.current(fallbackRole), before, after, idempotencyKey);
	}

	private void audit(
			String entityType,
			String entityId,
			String action,
			CurrentActorService.Actor actor,
			String before,
			String after,
			String idempotencyKey
	) {
		workflowAuditEventRepository.save(new WorkflowAuditEvent(
				entityType, entityId, action, actor.name(), actor.role(), before, after, idempotencyKey
		));
	}

	private String complaintState(ComplaintResponse complaint) {
		return "{\"status\":\"" + complaint.status() + "\",\"blocker\":\""
				+ (complaint.workflowBlocker() == null ? "" : complaint.workflowBlocker())
				+ "\",\"version\":" + complaint.version() + "}";
	}

	private String draftState(OfficialDraft draft, Complaint complaint) {
		return "{\"draftStatus\":\"" + draft.getStatus() + "\",\"complaintStatus\":\""
				+ complaint.getStatus() + "\",\"version\":" + draft.getVersion() + "}";
	}

	private String draftState(TrustDraftResponse draft, Complaint complaint) {
		return "{\"draftStatus\":\"" + draft.status() + "\",\"complaintStatus\":\""
				+ complaint.getStatus() + "\",\"version\":" + draft.version() + "}";
	}

	private Complaint getComplaint(UUID id) {
		return complaintRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + id));
	}

	private OfficialDraft getDraft(Long id) {
		return officialDraftRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Draft not found: " + id));
	}

	private String requireIdempotencyKey(String value) {
		if (value == null || value.isBlank() || value.length() > 200) {
			throw new IllegalArgumentException("Idempotency-Key header is required and must be at most 200 characters");
		}
		return value.trim();
	}

	private void requireVersion(long expected, long actual) {
		if (expected != actual) {
			throw new IllegalStateException("Version conflict: expected " + expected + " but was " + actual);
		}
	}

	private void requireSameHumanDecision(HumanReview existing, Long draftId, String actionPrefix) {
		if (!Objects.equals(existing.getOfficialDraftId(), draftId) || !existing.getAction().startsWith(actionPrefix)) {
			throw new IllegalStateException("Idempotency-Key was already used for another human decision");
		}
	}

	private void requireCompleted(IdempotencyRecord record) {
		if (record.isPending()) {
			throw new IllegalStateException("An operation with this Idempotency-Key is still in progress");
		}
	}

	private void runAfterCommit(UUID jobId) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			processingJobRunner.runAsync(jobId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				processingJobRunner.runAsync(jobId);
			}
		});
	}

	private void requireApprovedAttachmentDerivatives(UUID complaintId) {
		if (attachmentAnalysisRepository.existsByAttachment_Complaint_IdAndApprovedForAiFalse(complaintId)) {
			throw new IllegalStateException(
					"All attachments must pass malware scanning, extraction, and redaction before AI processing"
			);
		}
	}
}
