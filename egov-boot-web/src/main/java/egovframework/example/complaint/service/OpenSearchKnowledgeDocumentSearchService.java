package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.DocumentType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "opensearch")
public class OpenSearchKnowledgeDocumentSearchService implements KnowledgeDocumentSearchService {

	private final OpenSearchClient openSearchClient;
	private final KnowledgeDocumentRepository knowledgeDocumentRepository;
	private final String indexName;
	private final int resultSize;

	public OpenSearchKnowledgeDocumentSearchService(
			OpenSearchClient openSearchClient,
			KnowledgeDocumentRepository knowledgeDocumentRepository,
			@Value("${app.rag.opensearch.index-name}") String indexName,
			@Value("${app.rag.opensearch.result-size:3}") int resultSize
	) {
		this.openSearchClient = openSearchClient;
		this.knowledgeDocumentRepository = knowledgeDocumentRepository;
		this.indexName = indexName;
		this.resultSize = resultSize;
	}

	@Override
	public List<KnowledgeDocument> search(ComplaintAnalysis analysis) {
		try {
			SearchResponse<Map> response = openSearchClient.search(search -> search
							.index(indexName)
							.size(resultSize)
							.query(query -> query.multiMatch(multiMatch -> multiMatch
									.query(buildQuery(analysis))
									.fields("title^2", "content", "keywords", "legalBasis", "sourceName")
							)),
					Map.class);
			List<KnowledgeDocument> documents = response.hits().hits().stream()
					.map(Hit::source)
					.filter(source -> source != null)
					.map(this::toKnowledgeDocument)
					.toList();
			if (documents.isEmpty()) {
				return fallbackSearch(analysis);
			}
			return documents;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to search OpenSearch knowledge documents", exception);
		}
	}

	private String buildQuery(ComplaintAnalysis analysis) {
		return String.join(" ",
				analysis.getIntent(),
				analysis.getDepartment().getName(),
				analysis.getUrgency().name(),
				analysis.getLocationText() == null ? "" : analysis.getLocationText()
		).trim();
	}

	private KnowledgeDocument toKnowledgeDocument(Map source) {
		String title = text(source, "title", "OpenSearch knowledge document");
		return knowledgeDocumentRepository.findByTitle(title)
				.orElseGet(() -> knowledgeDocumentRepository.save(new KnowledgeDocument(
						documentType(source),
						title,
						text(source, "sourceName", "Amazon OpenSearch Serverless"),
						textOrNull(source, "sourceUrl"),
						text(source, "content", ""),
						text(source, "keywords", title),
						textOrNull(source, "legalBasis")
				)));
	}

	private List<KnowledgeDocument> fallbackSearch(ComplaintAnalysis analysis) {
		return knowledgeDocumentRepository.searchByKeyword(analysis.getIntent()).stream()
				.limit(resultSize)
				.toList();
	}

	private DocumentType documentType(Map source) {
		String value = text(source, "documentType", DocumentType.MANUAL.name());
		try {
			return DocumentType.valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException exception) {
			return DocumentType.MANUAL;
		}
	}

	private String text(Map source, String key, String defaultValue) {
		Object value = source.get(key);
		if (value == null || !StringUtils.hasText(String.valueOf(value))) {
			return defaultValue;
		}
		return String.valueOf(value);
	}

	private String textOrNull(Map source, String key) {
		Object value = source.get(key);
		return value == null || !StringUtils.hasText(String.valueOf(value)) ? null : String.valueOf(value);
	}
}
