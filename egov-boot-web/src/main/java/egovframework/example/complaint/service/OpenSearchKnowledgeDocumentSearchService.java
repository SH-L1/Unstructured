package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.KnowledgePurpose;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "opensearch")
public class OpenSearchKnowledgeDocumentSearchService implements KnowledgeDocumentSearchService {

	private final OpenSearchClient openSearchClient;
	private final KnowledgeDocumentRepository knowledgeDocumentRepository;
	private final String indexPrefix;
	private final int resultSize;
	private final int vectorK;
	private final String vectorField;
	private final String neuralModelId;
	private final String searchPipeline;
	private final ExternalCallGuard externalCallGuard;

	public OpenSearchKnowledgeDocumentSearchService(
			OpenSearchClient openSearchClient,
			KnowledgeDocumentRepository knowledgeDocumentRepository,
			ExternalCallGuard externalCallGuard,
			@Value("${app.rag.opensearch.index-prefix}") String indexPrefix,
			@Value("${app.rag.opensearch.result-size:10}") int resultSize,
			@Value("${app.rag.opensearch.vector-k:20}") int vectorK,
			@Value("${app.rag.opensearch.vector-field:embedding}") String vectorField,
			@Value("${app.rag.opensearch.neural-model-id}") String neuralModelId,
			@Value("${app.rag.opensearch.search-pipeline}") String searchPipeline
	) {
		this.openSearchClient = openSearchClient;
		this.knowledgeDocumentRepository = knowledgeDocumentRepository;
		this.externalCallGuard = externalCallGuard;
		this.indexPrefix = requireText(indexPrefix, "OpenSearch index prefix");
		this.resultSize = resultSize;
		this.vectorK = vectorK;
		this.vectorField = requireText(vectorField, "OpenSearch vector field");
		this.neuralModelId = requireText(neuralModelId, "OpenSearch neural model id");
		this.searchPipeline = requireText(searchPipeline, "OpenSearch hybrid rerank pipeline");
	}

	@Override
	public List<KnowledgeDocument> search(ComplaintAnalysis analysis) {
		return externalCallGuard.execute("opensearch", 0, () -> searchGuarded(analysis));
	}

	private List<KnowledgeDocument> searchGuarded(ComplaintAnalysis analysis) {
		try {
			String queryText = buildQuery(analysis);
			SearchResponse<Map> response = openSearchClient.search(search -> search
							.index(purposeIndexNames(indexPrefix))
							.ignoreUnavailable(true)
							.allowNoIndices(true)
							.pipeline(searchPipeline)
							.size(resultSize)
							.query(query -> query.hybrid(hybrid -> hybrid
									.queries(subQuery -> subQuery.multiMatch(multiMatch -> multiMatch
											.query(queryText)
											.fields("title^3", "provisionKey^3", "heading^2", "content",
													"keywords", "legalBasis^2", "sourceName")
									))
									.queries(subQuery -> subQuery.neural(neural -> neural
											.field(vectorField)
											.queryText(queryText)
											.modelId(neuralModelId)
											.k(vectorK)
									))
							)),
					Map.class);
			List<KnowledgeDocument> documents = response.hits().hits().stream()
					.map(Hit::source)
					.filter(source -> source != null)
					.map(this::resolveGovernedDocument)
					.flatMap(Optional::stream)
					.distinct()
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

	private Optional<KnowledgeDocument> resolveGovernedDocument(Map source) {
		Object documentId = source.get("documentId");
		if (documentId != null) {
			try {
				return knowledgeDocumentRepository.findById(Long.valueOf(String.valueOf(documentId)));
			}
			catch (NumberFormatException ignored) {
				return Optional.empty();
			}
		}
		Object title = source.get("title");
		if (title == null || String.valueOf(title).isBlank()) {
			return Optional.empty();
		}
		// The search index is a discovery aid. Candidates must exist in the
		// governed registry; deterministic gates decide whether they may support a claim.
		return knowledgeDocumentRepository.findByTitle(String.valueOf(title));
	}

	private List<KnowledgeDocument> fallbackSearch(ComplaintAnalysis analysis) {
		return knowledgeDocumentRepository.searchByKeyword(analysis.getIntent()).stream()
				.limit(resultSize)
				.toList();
	}

	static List<String> purposeIndexNames(String prefix) {
		return Arrays.stream(KnowledgePurpose.values())
				.filter(purpose -> purpose != KnowledgePurpose.UNVERIFIED_LEGACY)
				.map(purpose -> prefix + "-" + purpose.name().toLowerCase(Locale.ROOT).replace('_', '-'))
				.toList();
	}

	private static String requireText(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(label + " is required for hybrid OpenSearch retrieval");
		}
		return value.trim();
	}
}
