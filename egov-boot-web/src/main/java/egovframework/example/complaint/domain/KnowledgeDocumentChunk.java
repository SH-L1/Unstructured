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
@Table(name = "knowledge_document_chunks")
public class KnowledgeDocumentChunk extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "knowledge_document_id", nullable = false)
	private KnowledgeDocument knowledgeDocument;

	@Column(nullable = false)
	private int chunkIndex;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(nullable = false, length = 500)
	private String keywords;

	@Column(length = 500)
	private String legalBasis;

	@Column(length = 200)
	private String embeddingId;

	@Column
	private Integer tokenCount;

	@Column(nullable = false)
	private boolean active = true;

	protected KnowledgeDocumentChunk() {
	}

	public KnowledgeDocumentChunk(KnowledgeDocument knowledgeDocument, int chunkIndex, String content,
			String keywords, String legalBasis) {
		this.knowledgeDocument = knowledgeDocument;
		this.chunkIndex = chunkIndex;
		this.content = content;
		this.keywords = keywords;
		this.legalBasis = legalBasis;
	}

	public Long getId() {
		return id;
	}

	public KnowledgeDocument getKnowledgeDocument() {
		return knowledgeDocument;
	}

	public int getChunkIndex() {
		return chunkIndex;
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

	public String getEmbeddingId() {
		return embeddingId;
	}

	public Integer getTokenCount() {
		return tokenCount;
	}

	public boolean isActive() {
		return active;
	}
}
