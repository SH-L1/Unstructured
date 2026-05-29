package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "complaint_attachments")
public class ComplaintAttachment {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_id", nullable = false)
	private Complaint complaint;

	@Column(nullable = false, length = 255)
	private String originalFilename;

	@Column(length = 100)
	private String contentType;

	@Column(nullable = false)
	private long size;

	@Column(nullable = false, length = 500)
	private String storageKey;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ComplaintAttachment() {
	}

	public ComplaintAttachment(Complaint complaint, String originalFilename, String contentType, long size, String storageKey) {
		this.complaint = complaint;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.size = size;
		this.storageKey = storageKey;
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

	public UUID getId() {
		return id;
	}

	public UUID getComplaintId() {
		return complaint.getId();
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSize() {
		return size;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
