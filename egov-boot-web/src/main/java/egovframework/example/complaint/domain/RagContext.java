package egovframework.example.complaint.domain;

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
@Table(name = "rag_contexts")
public class RagContext extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_id", nullable = false)
	private Complaint complaint;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "official_draft_id")
	private OfficialDraft officialDraft;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "knowledge_document_id", nullable = false)
	private KnowledgeDocument knowledgeDocument;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "knowledge_document_chunk_id")
	private KnowledgeDocumentChunk knowledgeDocumentChunk;

	@Column(length = 500)
	private String legalBasis;

	@Column(nullable = false, columnDefinition = "text")
	private String contentSnippet;

	protected RagContext() {
	}

	public RagContext(Complaint complaint, OfficialDraft officialDraft, KnowledgeDocument knowledgeDocument,
			String legalBasis, String contentSnippet) {
		this(complaint, officialDraft, knowledgeDocument, null, legalBasis, contentSnippet);
	}

	public RagContext(Complaint complaint, OfficialDraft officialDraft, KnowledgeDocument knowledgeDocument,
			KnowledgeDocumentChunk knowledgeDocumentChunk, String legalBasis, String contentSnippet) {
		this.complaint = complaint;
		this.officialDraft = officialDraft;
		this.knowledgeDocument = knowledgeDocument;
		this.knowledgeDocumentChunk = knowledgeDocumentChunk;
		this.legalBasis = legalBasis;
		this.contentSnippet = contentSnippet;
	}

	public KnowledgeDocument getKnowledgeDocument() {
		return knowledgeDocument;
	}

	public KnowledgeDocumentChunk getKnowledgeDocumentChunk() {
		return knowledgeDocumentChunk;
	}

	public String getLegalBasis() {
		return legalBasis;
	}

	public String getContentSnippet() {
		return contentSnippet;
	}

}
