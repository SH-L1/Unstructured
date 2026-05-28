package com.school.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "complaints")
public class Complaint {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 50)
	private String sourceChannel;

	@Column(nullable = false, columnDefinition = "text")
	private String rawText;

	@Column(length = 500)
	private String locationText;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ComplaintStatus status;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Complaint() {
	}

	public Complaint(String sourceChannel, String rawText, String locationText) {
		this.sourceChannel = sourceChannel;
		this.rawText = rawText;
		this.locationText = locationText;
		this.status = ComplaintStatus.RECEIVED;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (status == null) {
			status = ComplaintStatus.RECEIVED;
		}
	}

	public UUID getId() {
		return id;
	}

	public String getSourceChannel() {
		return sourceChannel;
	}

	public String getRawText() {
		return rawText;
	}

	public String getLocationText() {
		return locationText;
	}

	public ComplaintStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
