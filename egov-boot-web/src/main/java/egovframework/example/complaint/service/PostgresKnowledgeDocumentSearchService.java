package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "postgres-mock", matchIfMissing = true)
public class PostgresKnowledgeDocumentSearchService implements KnowledgeDocumentSearchService {

	private final KnowledgeDocumentRepository knowledgeDocumentRepository;

	public PostgresKnowledgeDocumentSearchService(KnowledgeDocumentRepository knowledgeDocumentRepository) {
		this.knowledgeDocumentRepository = knowledgeDocumentRepository;
	}

	@Override
	public List<KnowledgeDocument> search(ComplaintAnalysis analysis) {
		Map<Long, KnowledgeDocument> documents = new LinkedHashMap<>();
		for (String keyword : List.of("waste", "dumping", analysis.getIntent())) {
			knowledgeDocumentRepository.searchByKeyword(keyword)
					.forEach(document -> documents.put(document.getId(), document));
		}
		if (documents.isEmpty()) {
			return knowledgeDocumentRepository.findAll().stream()
					.sorted(Comparator.comparing(KnowledgeDocument::getId))
					.limit(3)
					.toList();
		}
		return documents.values().stream().limit(3).toList();
	}
}
