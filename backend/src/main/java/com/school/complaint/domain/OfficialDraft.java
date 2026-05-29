package com.school.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "official_drafts")
public class OfficialDraft extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_id", nullable = false)
	private Complaint complaint;

	@Column(nullable = false, columnDefinition = "text")
	private String draftText;

	@Column(nullable = false, length = 100)
	private String modelName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DraftStatus status;

	protected OfficialDraft() {
	}

	public OfficialDraft(Complaint complaint, String draftText, String modelName) {
		this.complaint = complaint;
		this.draftText = draftText;
		this.modelName = modelName;
		this.status = DraftStatus.DRAFT;
	}

	public void revise(String revisedText) {
		this.draftText = revisedText;
		this.status = DraftStatus.REVISED;
	}

	public Long getId() {
		return id;
	}

	public Complaint getComplaint() {
		return complaint;
	}

	public String getDraftText() {
		return draftText;
	}

	public String getModelName() {
		return modelName;
	}

	public DraftStatus getStatus() {
		return status;
	}
}
