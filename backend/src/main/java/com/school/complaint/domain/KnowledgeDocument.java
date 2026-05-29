package com.school.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DocumentType documentType;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 200)
	private String sourceName;

	@Column(length = 500)
	private String sourceUrl;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(nullable = false, length = 500)
	private String keywords;

	@Column(length = 500)
	private String legalBasis;

	protected KnowledgeDocument() {
	}

	public KnowledgeDocument(DocumentType documentType, String title, String sourceName, String sourceUrl,
			String content, String keywords, String legalBasis) {
		this.documentType = documentType;
		this.title = title;
		this.sourceName = sourceName;
		this.sourceUrl = sourceUrl;
		this.content = content;
		this.keywords = keywords;
		this.legalBasis = legalBasis;
	}

	public Long getId() {
		return id;
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public String getTitle() {
		return title;
	}

	public String getSourceName() {
		return sourceName;
	}

	public String getContent() {
		return content;
	}

	public String getKeywords() {
		return keywords;
	}

	public String getLegalBasis() {
		return legalBasis;
	}
}
