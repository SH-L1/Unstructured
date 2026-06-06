package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workflow_audit_events")
public class WorkflowAuditEvent {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 60)
	private String entityType;

	@Column(nullable = false, length = 200)
	private String entityId;

	@Column(nullable = false, length = 80)
	private String action;

	@Column(nullable = false, length = 100)
	private String actor;

	@Column(nullable = false, length = 40)
	private String actorRole;

	@Column(columnDefinition = "text")
	private String beforeValue;

	@Column(nullable = false, columnDefinition = "text")
	private String afterValue;

	@Column(length = 200)
	private String idempotencyKey;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected WorkflowAuditEvent() {
	}

	public WorkflowAuditEvent(
			String entityType,
			String entityId,
			String action,
			String actor,
			String actorRole,
			String beforeValue,
			String afterValue,
			String idempotencyKey
	) {
		this.entityType = entityType;
		this.entityId = entityId;
		this.action = action;
		this.actor = actor;
		this.actorRole = actorRole;
		this.beforeValue = beforeValue;
		this.afterValue = afterValue;
		this.idempotencyKey = idempotencyKey;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
