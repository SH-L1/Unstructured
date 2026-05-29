package com.school.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "draft_revisions")
public class DraftRevision extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "official_draft_id", nullable = false)
	private OfficialDraft officialDraft;

	@Column(nullable = false, columnDefinition = "text")
	private String beforeText;

	@Column(nullable = false, columnDefinition = "text")
	private String afterText;

	@Column(nullable = false, length = 100)
	private String revisedBy;

	protected DraftRevision() {
	}

	public DraftRevision(OfficialDraft officialDraft, String beforeText, String afterText, String revisedBy) {
		this.officialDraft = officialDraft;
		this.beforeText = beforeText;
		this.afterText = afterText;
		this.revisedBy = revisedBy;
	}

	public Long getId() {
		return id;
	}

	public String getBeforeText() {
		return beforeText;
	}

	public String getAfterText() {
		return afterText;
	}

	public String getRevisedBy() {
		return revisedBy;
	}
}
