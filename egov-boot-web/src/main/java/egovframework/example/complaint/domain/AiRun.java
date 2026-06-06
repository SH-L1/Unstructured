package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ai_runs")
public class AiRun extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false)
	private UUID complaintId;

	private UUID processingJobId;

	@Column(nullable = false, length = 40)
	private String taskType;

	@Column(nullable = false, length = 80)
	private String provider;

	@Column(nullable = false, length = 100)
	private String modelName;

	@Column(nullable = false, length = 80)
	private String promptVersion;

	@Column(nullable = false, length = 80)
	private String schemaVersion;

	@Column(nullable = false, length = 128)
	private String inputHash;

	@Column(length = 128)
	private String outputHash;

	@Column(nullable = false, length = 40)
	private String status;

	@Column(nullable = false)
	private long costUnits;

	@Column(nullable = false)
	private long durationMs;

	@Column(nullable = false)
	private int retryCount;

	@Column(columnDefinition = "text")
	private String failureReason;

	protected AiRun() {
	}

	public AiRun(UUID complaintId, UUID processingJobId, String taskType, String provider, String modelName,
			String promptVersion, String schemaVersion, String inputHash, long costUnits) {
		this.complaintId = complaintId;
		this.processingJobId = processingJobId;
		this.taskType = taskType;
		this.provider = provider;
		this.modelName = modelName;
		this.promptVersion = promptVersion;
		this.schemaVersion = schemaVersion;
		this.inputHash = inputHash;
		this.costUnits = costUnits;
		this.status = "RUNNING";
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public void succeed(String outputHash, long durationMs, int retryCount) {
		this.outputHash = outputHash;
		this.durationMs = durationMs;
		this.retryCount = retryCount;
		this.status = "SUCCEEDED";
	}

	public void updateInputHash(String inputHash) {
		if (inputHash == null || inputHash.isBlank()) {
			throw new IllegalArgumentException("AI input hash is required");
		}
		this.inputHash = inputHash;
	}

	public void fail(String reason, long durationMs, int retryCount) {
		this.failureReason = reason;
		this.durationMs = durationMs;
		this.retryCount = retryCount;
		this.status = "FAILED";
	}

	public String getTaskType() {
		return taskType;
	}

	public String getProvider() {
		return provider;
	}

	public String getModelName() {
		return modelName;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	public String getSchemaVersion() {
		return schemaVersion;
	}

	public String getInputHash() {
		return inputHash;
	}

	public String getOutputHash() {
		return outputHash;
	}

	public String getStatus() {
		return status;
	}

	public long getCostUnits() {
		return costUnits;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public String getFailureReason() {
		return failureReason;
	}
}
