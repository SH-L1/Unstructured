package com.school.complaint.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "complaints")
public class Complaint extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SourceChannel sourceChannel;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String rawText;

	@Column(length = 500)
	private String locationText;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ComplaintStatus status;

	@OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ComplaintAttachment> attachments = new ArrayList<>();

	protected Complaint() {
	}

	public Complaint(SourceChannel sourceChannel, String title, String rawText, String locationText) {
		this.sourceChannel = sourceChannel;
		this.title = title;
		this.rawText = rawText;
		this.locationText = locationText;
		this.status = ComplaintStatus.RECEIVED;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (status == null) {
			status = ComplaintStatus.RECEIVED;
		}
	}

	public void markAnalyzed() {
		status = ComplaintStatus.ANALYZED;
	}

	public void markDraftGenerated() {
		status = ComplaintStatus.DRAFT_GENERATED;
	}

	public UUID getId() {
		return id;
	}

	public SourceChannel getSourceChannel() {
		return sourceChannel;
	}

	public String getTitle() {
		return title;
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

	public List<ComplaintAttachment> getAttachments() {
		return attachments;
	}
}
