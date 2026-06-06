package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String operation;

	@Column(nullable = false, length = 200)
	private String idempotencyKey;

	@Column(nullable = false, length = 200)
	private String resourceId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected IdempotencyRecord() {
	}

	public IdempotencyRecord(String operation, String idempotencyKey, String resourceId) {
		this.operation = operation;
		this.idempotencyKey = idempotencyKey;
		this.resourceId = resourceId;
	}

	public static IdempotencyRecord pending(String operation, String idempotencyKey, String targetReference) {
		String reference = targetReference == null || targetReference.isBlank()
				? "PENDING"
				: "PENDING:" + targetReference;
		return new IdempotencyRecord(operation, idempotencyKey, reference);
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

	public String getResourceId() {
		return resourceId;
	}

	public boolean isPending() {
		return resourceId.startsWith("PENDING");
	}

	public void complete(String resourceId) {
		if (resourceId == null || resourceId.isBlank() || resourceId.length() > 200) {
			throw new IllegalArgumentException("Idempotent resource id is required and must be at most 200 characters");
		}
		this.resourceId = resourceId;
	}
}
