package egovframework.example.complaint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.domain.Complaint;
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
public class OpenAiComplaintAnalysisClient implements ComplaintAnalysisClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String model;

	public OpenAiComplaintAnalysisClient(
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
	public ComplaintAnalysisResult analyze(Complaint complaint) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("OPENAI_API_KEY is required when app.ai.provider=openai");
		}
		try {
			Map<String, Object> body = Map.of(
					"model", model,
					"instructions", """
							You analyze Korean civil complaints for a local-government complaint system.
							Return only a single JSON object, no markdown.
							Fields:
							intent: short Korean summary of the complaint intent.
							urgency: one of LOW, NORMAL, HIGH, EMERGENCY.
							sentiment: one of NEUTRAL, DISCOMFORT, ANGER, ANXIETY.
							departmentCode: one of SAFETY_CONTROL, RESOURCE_RECYCLING, ROAD, TRAFFIC, CIVIL_AFFAIRS.
							locationText: location from the complaint, or null.
							geoJson: GeoJSON Feature string when location exists; geometry may be null.
							keywords: array of Korean/English search keywords for RAG.
							requiredAction: concise next action.
							If the complaint mentions bomb, explosive, biohazard, biochemical, hazardous chemical, suspicious substance, or public safety threat, classify it as EMERGENCY and departmentCode SAFETY_CONTROL.
							""",
					"input", """
							Complaint:
							%s

							Location:
							%s
							""".formatted(complaint.getRawText(), complaint.getLocationText()),
					"max_output_tokens", 900
			);
			String response = restClient.post()
					.uri("/responses")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
					.body(body)
					.retrieve()
					.body(String.class);
			JsonNode result = extractJsonObject(response);
			return new ComplaintAnalysisResult(
					requiredText(result, "intent"),
					requiredText(result, "urgency"),
					requiredText(result, "sentiment"),
					requiredText(result, "departmentCode"),
					textOrDefault(result, "locationText", complaint.getLocationText()),
					textOrNull(result, "geoJson"),
					result.toString()
			);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to analyze complaint with OpenAI", exception);
		}
	}

	private JsonNode extractJsonObject(String responseBody) throws Exception {
		JsonNode root = objectMapper.readTree(responseBody);
		String text = root.path("output_text").asText();
		if (text == null || text.isBlank()) {
			text = extractOutputText(root);
		}
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new IllegalStateException("OpenAI analysis text did not contain a JSON object");
		}
		return objectMapper.readTree(text.substring(start, end + 1));
	}

	private String extractOutputText(JsonNode root) {
		StringBuilder builder = new StringBuilder();
		for (JsonNode output : root.path("output")) {
			for (JsonNode content : output.path("content")) {
				JsonNode text = content.path("text");
				if (text.isTextual()) {
					builder.append(text.asText());
				}
			}
		}
		if (builder.isEmpty()) {
			throw new IllegalStateException("OpenAI response did not contain output text");
		}
		return builder.toString();
	}

	private String requiredText(JsonNode node, String fieldName) {
		String value = node.path(fieldName).asText();
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("OpenAI analysis is missing field: " + fieldName);
		}
		return value;
	}

	private String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
		JsonNode value = node.path(fieldName);
		return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? defaultValue : value.asText();
	}

	private String textOrNull(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		return value.isTextual() ? value.asText() : value.toString();
	}
}
