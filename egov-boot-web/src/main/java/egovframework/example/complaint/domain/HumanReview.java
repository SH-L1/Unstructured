package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "human_reviews")
public class HumanReview {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false)
	private UUID complaintId;

	private Long officialDraftId;

	@Column(nullable = false, length = 40)
	private String action;

	@Column(nullable = false, length = 100)
	private String actor;

	@Column(nullable = false, length = 40)
	private String actorRole;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(nullable = false, length = 200)
	private String idempotencyKey;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected HumanReview() {
	}

	public HumanReview(UUID complaintId, Long officialDraftId, String action, String actor, String actorRole,
			String notes, String idempotencyKey) {
		this.complaintId = complaintId;
		this.officialDraftId = officialDraftId;
		this.action = action;
		this.actor = actor;
		this.actorRole = actorRole;
		this.notes = notes;
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

	public String getAction() {
		return action;
	}

	public Long getOfficialDraftId() {
		return officialDraftId;
	}

	public String getActor() {
		return actor;
	}

	public String getActorRole() {
		return actorRole;
	}

	public String getNotes() {
		return notes;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
