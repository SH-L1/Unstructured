package egovframework.example.complaint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.KnowledgeDocument;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiDraftGenerationClient implements DraftGenerationClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String model;

	public OpenAiDraftGenerationClient(
			ObjectMapper objectMapper,
			@Value("${app.openai.base-url:https://api.openai.com/v1}") String baseUrl,
			@Value("${app.openai.api-key:}") String apiKey,
			@Value("${app.openai.model:gpt-4o-mini}") String model
	) {
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.model = model;
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	@Override
	public String generateDraft(Complaint complaint, ComplaintAnalysis analysis, List<KnowledgeDocument> documents) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("OPENAI_API_KEY is required when app.ai.provider=openai");
		}
		try {
			Map<String, Object> body = Map.of(
					"model", model,
					"instructions", """
							You draft Korean public-office civil complaint replies for staff review.
							Output plain Korean text only. Do not use Markdown, bullets made with hyphens, headings with #, bold markers, tables, code blocks, or decorative separators.
							Write it like an official civil complaint reply draft that can be pasted into a public office document.
							Use concise labeled lines such as 제목:, 수신:, 접수내용:, 검토의견:, 처리계획:, 안내사항: when useful.
							Use only the provided RAG references. Do not invent laws or facts.
							If references are missing or weak, explicitly say that immediate field verification and competent-agency transfer/review are required instead of citing unrelated law.
							For bomb, explosive, biochemical, hazardous material, or public safety threat complaints, write an urgent safety response draft that references emergency reporting, site control, and transfer to police/fire/safety departments.
							""",
					"input", buildPrompt(complaint, analysis, documents),
					"max_output_tokens", 1200
			);
			String response = restClient.post()
					.uri("/responses")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
					.body(body)
					.retrieve()
					.body(String.class);
			String text = extractOutputText(objectMapper.readTree(response));
			if (text.isBlank()) {
				throw new IllegalStateException("OpenAI response did not contain draft text");
			}
			return normalizeOfficialDraft(text);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to generate draft with OpenAI", exception);
		}
	}

	private String buildPrompt(Complaint complaint, ComplaintAnalysis analysis, List<KnowledgeDocument> documents) {
		StringBuilder references = new StringBuilder();
		if (documents.isEmpty()) {
			references.append("No directly relevant RAG references were found.\n");
		}
		for (KnowledgeDocument document : documents) {
			references.append("- Title: ").append(document.getTitle()).append('\n')
					.append("  Type: ").append(document.getDocumentType()).append('\n')
					.append("  Legal basis: ").append(document.getLegalBasis()).append('\n')
					.append("  Content: ").append(document.getContent()).append("\n\n");
		}
		return """
				Complaint:
				%s

				Location:
				%s

				Analysis:
				Intent: %s
				Type: %s
				Urgency: %s
				Sentiment: %s
				Responsible department: %s

				RAG references:
				%s
				""".formatted(
				complaint.getRawText(),
				complaint.getLocationText(),
				analysis.getIntent(),
				analysis.getComplaintType(),
				analysis.getUrgency(),
				analysis.getSentiment(),
				analysis.getDepartment().getName(),
				references
		).trim();
	}

	private String extractOutputText(JsonNode root) {
		String text = root.path("output_text").asText();
		if (text != null && !text.isBlank()) {
			return text;
		}
		StringBuilder builder = new StringBuilder();
		for (JsonNode output : root.path("output")) {
			for (JsonNode content : output.path("content")) {
				JsonNode contentText = content.path("text");
				if (contentText.isTextual()) {
					builder.append(contentText.asText());
				}
			}
		}
		return builder.toString();
	}

	private String normalizeOfficialDraft(String value) {
		return value
				.replace("```", "")
				.replaceAll("(?m)^#{1,6}\\s*", "")
				.replaceAll("\\*\\*(.*?)\\*\\*", "$1")
				.replaceAll("__(.*?)__", "$1")
				.replaceAll("(?m)^\\s*[-*]\\s+", "")
				.replaceAll("(?m)^\\s*>\\s?", "")
				.replaceAll("\\n{3,}", "\n\n")
				.trim();
	}
}
