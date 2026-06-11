package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.ComplaintType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.KnowledgeDocumentChunk;
import egovframework.example.complaint.repository.KnowledgeDocumentChunkRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "postgres", matchIfMissing = true)
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
		String intent = value(analysis.getIntent());
		String rawText = analysis.getComplaint() == null ? "" : value(analysis.getComplaint().getRawText());
		String redactedText = analysis.getComplaint() == null ? "" : value(analysis.getComplaint().getRedactedText());
		String combined = (intent + " " + rawText + " " + redactedText).toLowerCase();

		List<String> keywords = new ArrayList<>();
		if (containsAny(combined, "화장실", "공중화장실", "toilet", "restroom")) {
			keywords.addAll(List.of("공중화장실", "화장실", "위생", "청소", "toilet", "restroom"));
		}
		keywords.addAll(switch (type) {
			case ILLEGAL_DUMPING -> List.of(
					"waste", "dumping", "garbage", "trash", "쓰레기", "폐기물", "무단투기", "생활폐기물", "폐기물관리법", intent
			);
			case ROAD_DAMAGE -> List.of(
					"road", "pothole", "sidewalk", "도로", "포트홀", "파손", "보도", "가로등", "도로법", intent
			);
			case ILLEGAL_PARKING -> List.of(
					"parking", "illegal parking", "불법주정차", "주정차", "주차", "교통", "도로교통법", "주차장법", intent
			);
			case TRAFFIC_SIGN -> List.of(
					"traffic sign", "signal", "sign", "교통표지", "표지판", "신호", "교통", "도로교통법", intent
			);
			case NOISE -> List.of(
					"noise", "소음", "진동", "생활불편", "소음ㆍ진동관리법", intent
			);
			case ENVIRONMENT -> List.of(
					"environment", "pollution", "환경", "오염", "악취", "대기", "먼지", "악취방지법", "대기환경보전법", "물환경보전법", intent
			);
			case HAZARDOUS_MATERIAL -> List.of(
					"biohazard", "hazardous", "chemical", "emergency", "위험", "안전", "재난", "화학물질", "위험물",
					"화학물질관리법", "위험물안전관리법", "재난 및 안전관리 기본법", intent
			);
			case GENERAL -> List.of(
					"민원", "생활불편", "현장확인", "민원 처리에 관한 법률", "행정절차법", intent
			);
		});
		return keywords.stream()
				.filter(value -> value != null && !value.isBlank())
				.distinct()
				.limit(40)
				.toList();
	}

	private static String value(String text) {
		return text == null ? "" : text;
	}

	private static boolean containsAny(String value, String... needles) {
		for (String needle : needles) {
			if (value.contains(needle.toLowerCase())) {
				return true;
			}
		}
		return false;
	}
}
