package egovframework.example.complaint.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.KnowledgeDocument;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock-bedrock")
public class ReviewOnlyMockDraftGenerationClient implements DraftGenerationClient {

	private final ObjectMapper objectMapper;

	public ReviewOnlyMockDraftGenerationClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public String generateDraft(
			Complaint complaint,
			ComplaintAnalysis analysis,
			List<KnowledgeDocument> documents,
			List<String> approvedAttachmentTexts
	) {
		String evidenceTitles = documents.stream()
				.map(KnowledgeDocument::getTitle)
				.reduce((left, right) -> left + ", " + right)
				.orElseThrow(() -> new IllegalStateException("Verified evidence is required"));
		List<String> evidenceIds = documents.stream().map(document -> String.valueOf(document.getId())).toList();
		try {
			return objectMapper.writeValueAsString(java.util.Map.of(
					"schemaVersion", "draft-claims-v1",
					"claims", List.of(
							claim("Staff review required.", "REVIEW_NOTICE", evidenceIds),
							claim("Receipt summary: " + analysis.getIntent(), "ACKNOWLEDGEMENT", evidenceIds),
							claim("Recommended department: " + analysis.getDepartment().getName(),
									"PROPOSED_NEXT_STEP", evidenceIds),
							claim("Verified evidence reviewed: " + evidenceTitles,
									"EVIDENCE_BASED_FINDING", evidenceIds),
							claim("No action, acceptance, dispatch, or completion has been performed automatically.",
									"REVIEW_NOTICE", evidenceIds)
					)
			));
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to create structured mock draft", exception);
		}
	}

	private java.util.Map<String, Object> claim(String text, String type, List<String> evidenceIds) {
		return java.util.Map.of("text", text, "claimType", type, "evidenceIds", evidenceIds);
	}
}
