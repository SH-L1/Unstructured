package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processing_jobs")
public class ProcessingJob extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_id", nullable = false)
	private Complaint complaint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private ProcessingJobType jobType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private ProcessingJobStatus status;

	@Column(nullable = false, length = 200)
	private String idempotencyKey;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false)
	private int maxAttempts;

	private LocalDateTime leaseUntil;

	@Column(columnDefinition = "text")
	private String failureReason;

	@Column(length = 200)
	private String resultReference;

	@Column(length = 500)
	private String payloadReference;

	@Column(nullable = false)
	private long costUnits;

	@Version
	@Column(nullable = false)
	private long version;

	protected ProcessingJob() {
	}

	public ProcessingJob(Complaint complaint, ProcessingJobType jobType, String idempotencyKey, int maxAttempts) {
		this(complaint, jobType, idempotencyKey, maxAttempts, null);
	}

	public ProcessingJob(
			Complaint complaint,
			ProcessingJobType jobType,
			String idempotencyKey,
			int maxAttempts,
			String payloadReference
	) {
		this.complaint = complaint;
		this.jobType = jobType;
		this.idempotencyKey = idempotencyKey;
		this.maxAttempts = maxAttempts;
		this.payloadReference = payloadReference;
		this.status = ProcessingJobStatus.PENDING;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public void start(LocalDateTime leaseUntil) {
		if (status != ProcessingJobStatus.PENDING && status != ProcessingJobStatus.FAILED) {
			throw new IllegalStateException("Job cannot be started from " + status);
		}
		if (attempts >= maxAttempts) {
			throw new IllegalStateException("Job retry limit reached");
		}
		attempts++;
		status = ProcessingJobStatus.RUNNING;
		this.leaseUntil = leaseUntil;
		failureReason = null;
	}

	public void succeed(String resultReference) {
		requireRunning();
		status = ProcessingJobStatus.SUCCEEDED;
		this.resultReference = resultReference;
		leaseUntil = null;
	}

	public void fail(String reason, boolean blocked) {
		fail(reason, blocked, null);
	}

	public void fail(String reason, boolean blocked, LocalDateTime retryAt) {
		requireRunning();
		status = blocked ? ProcessingJobStatus.BLOCKED : ProcessingJobStatus.FAILED;
		failureReason = reason;
		leaseUntil = blocked ? null : retryAt;
	}

	public boolean expireLease(LocalDateTime now) {
		return expireLease(now, null);
	}

	public boolean expireLease(LocalDateTime now, LocalDateTime retryAt) {
		if (status != ProcessingJobStatus.RUNNING || leaseUntil == null || !leaseUntil.isBefore(now)) {
			return false;
		}
		status = attempts >= maxAttempts ? ProcessingJobStatus.BLOCKED : ProcessingJobStatus.FAILED;
		failureReason = "Worker lease expired before completion";
		leaseUntil = status == ProcessingJobStatus.BLOCKED ? null : retryAt;
		return true;
	}

	private void requireRunning() {
		if (status != ProcessingJobStatus.RUNNING) {
			throw new IllegalStateException("Job is not running");
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getComplaintId() {
		return complaint.getId();
	}

	public ProcessingJobType getJobType() {
		return jobType;
	}

	public ProcessingJobStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public String getResultReference() {
		return resultReference;
	}

	public String getPayloadReference() {
		return payloadReference;
	}

	public LocalDateTime getLeaseUntil() {
		return leaseUntil;
	}

	public long getVersion() {
		return version;
	}
}
