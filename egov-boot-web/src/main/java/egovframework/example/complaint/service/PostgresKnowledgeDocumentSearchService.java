package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.ComplaintType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.KnowledgeDocumentChunk;
import egovframework.example.complaint.repository.KnowledgeDocumentChunkRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "postgres-mock", matchIfMissing = true)
public class PostgresKnowledgeDocumentSearchService implements KnowledgeDocumentSearchService {

	private final KnowledgeDocumentRepository knowledgeDocumentRepository;
	private final KnowledgeDocumentChunkRepository knowledgeDocumentChunkRepository;

	public PostgresKnowledgeDocumentSearchService(
			KnowledgeDocumentRepository knowledgeDocumentRepository,
			KnowledgeDocumentChunkRepository knowledgeDocumentChunkRepository
	) {
		this.knowledgeDocumentRepository = knowledgeDocumentRepository;
		this.knowledgeDocumentChunkRepository = knowledgeDocumentChunkRepository;
	}

	@Override
	public List<KnowledgeDocument> search(ComplaintAnalysis analysis) {
		Map<Long, KnowledgeDocument> documents = new LinkedHashMap<>();
		for (String keyword : keywordsFor(analysis)) {
			if (keyword == null || keyword.isBlank()) {
				continue;
			}
			knowledgeDocumentChunkRepository.searchByKeyword(keyword).stream()
					.map(KnowledgeDocumentChunk::getKnowledgeDocument)
					.forEach(document -> documents.put(document.getId(), document));
			knowledgeDocumentRepository.searchByKeyword(keyword)
					.forEach(document -> documents.put(document.getId(), document));
		}
		return documents.values().stream()
				.limit(10)
				.toList();
	}

	private List<String> keywordsFor(ComplaintAnalysis analysis) {
		ComplaintType type = analysis.getComplaintType();
		return switch (type) {
			case ILLEGAL_DUMPING -> List.of("waste", "dumping", "garbage", "trash", "쓰레기", "폐기물", "무단투기", analysis.getIntent());
			case ROAD_DAMAGE -> List.of("road", "pothole", "sidewalk", "도로", "포트홀", "파손", "보도", analysis.getIntent());
			case ILLEGAL_PARKING -> List.of("parking", "illegal parking", "불법주정차", "주차", "교통", analysis.getIntent());
			case TRAFFIC_SIGN -> List.of("traffic sign", "signal", "sign", "교통표지", "신호", analysis.getIntent());
			case NOISE -> List.of("noise", "소음", "생활불편", analysis.getIntent());
			case ENVIRONMENT -> List.of("environment", "pollution", "환경", "오염", "악취", analysis.getIntent());
			case HAZARDOUS_MATERIAL -> List.of(
					"biohazard", "biochemical", "hazardous", "chemical", "bomb", "explosive", "emergency",
					"생화학", "위험물", "폭탄", "폭발물", "화학물질", "유해물질", "재난", "경찰", "소방",
					analysis.getIntent()
			);
			default -> List.of(analysis.getIntent());
		};
	}
}
