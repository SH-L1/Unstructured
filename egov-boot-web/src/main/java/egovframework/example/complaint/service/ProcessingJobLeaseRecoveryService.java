package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ProcessingJob;
import egovframework.example.complaint.domain.ProcessingJobStatus;
import egovframework.example.complaint.domain.WorkflowAuditEvent;
import egovframework.example.complaint.domain.WorkflowBlocker;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.ProcessingJobRepository;
import egovframework.example.complaint.repository.WorkflowAuditEventRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingJobLeaseRecoveryService {

	private final ProcessingJobRepository processingJobRepository;
	private final ComplaintRepository complaintRepository;
	private final WorkflowAuditEventRepository workflowAuditEventRepository;
	private final long retryBaseSeconds;
	private final long retryMaxSeconds;

	public ProcessingJobLeaseRecoveryService(
			ProcessingJobRepository processingJobRepository,
			ComplaintRepository complaintRepository,
			WorkflowAuditEventRepository workflowAuditEventRepository,
			@Value("${app.worker.retry-base-seconds:2}") long retryBaseSeconds,
			@Value("${app.worker.retry-max-seconds:60}") long retryMaxSeconds
	) {
		this.processingJobRepository = processingJobRepository;
		this.complaintRepository = complaintRepository;
		this.workflowAuditEventRepository = workflowAuditEventRepository;
		this.retryBaseSeconds = Math.max(1, retryBaseSeconds);
		this.retryMaxSeconds = Math.max(this.retryBaseSeconds, retryMaxSeconds);
	}

	@Transactional
	@Scheduled(
			initialDelayString = "${app.jobs.lease-recovery-initial-delay-ms:60000}",
			fixedDelayString = "${app.jobs.lease-recovery-delay-ms:60000}"
	)
	public void recoverExpiredLeases() {
		LocalDateTime now = LocalDateTime.now();
		for (ProcessingJob job : processingJobRepository.findByStatusAndLeaseUntilBefore(ProcessingJobStatus.RUNNING, now)) {
			String before = jobState(job);
			if (job.expireLease(now, now.plusSeconds(retryDelaySeconds(job.getAttempts())))) {
				ProcessingJob saved = processingJobRepository.saveAndFlush(job);
				audit("PROCESSING_JOB", saved.getId().toString(), "LEASE_EXPIRED",
						before, jobState(saved), saved.getIdempotencyKey());
			}
		}
		for (ProcessingJob job : processingJobRepository.findByStatus(ProcessingJobStatus.BLOCKED)) {
			Complaint complaint = complaintRepository.findById(job.getComplaintId()).orElse(null);
			if (complaint != null && complaint.getWorkflowBlocker() == null) {
				String before = complaintState(complaint);
				complaint.block(blockerFor(job.getFailureReason()));
				Complaint saved = complaintRepository.saveAndFlush(complaint);
				audit("COMPLAINT", saved.getId().toString(), "BLOCKED_JOB_RECONCILED",
						before, complaintState(saved), job.getIdempotencyKey());
			}
		}
	}

	private long retryDelaySeconds(int attempts) {
		long multiplier = 1L << Math.min(20, Math.max(0, attempts - 1));
		return multiplier > retryMaxSeconds / retryBaseSeconds
				? retryMaxSeconds
				: Math.min(retryMaxSeconds, retryBaseSeconds * multiplier);
	}

	private WorkflowBlocker blockerFor(String reason) {
		if (reason != null && (reason.contains("Verified official evidence") || reason.contains("evidence snapshot"))) {
			return WorkflowBlocker.EVIDENCE_INSUFFICIENT;
		}
		if (reason != null && reason.contains("Conflicting official evidence")) {
			return WorkflowBlocker.CONFLICT_DETECTED;
		}
		if (reason != null && reason.contains("jurisdiction")) {
			return WorkflowBlocker.NEEDS_JURISDICTION;
		}
		return WorkflowBlocker.PROCESSING_FAILED;
	}

	private void audit(String entityType, String entityId, String action, String before, String after,
			String idempotencyKey) {
		workflowAuditEventRepository.save(new WorkflowAuditEvent(
				entityType,
				entityId,
				action,
				"system-lease-recovery",
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

	private String jobState(ProcessingJob job) {
		return "{\"status\":\"" + job.getStatus() + "\",\"attempts\":" + job.getAttempts()
				+ ",\"version\":" + job.getVersion() + "}";
	}
}
