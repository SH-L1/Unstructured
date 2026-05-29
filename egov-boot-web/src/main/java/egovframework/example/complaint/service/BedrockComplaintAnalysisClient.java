package egovframework.example.complaint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.domain.Complaint;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Service
@ConditionalOnProperty(name = "app.aws.bedrock.enabled", havingValue = "true")
public class BedrockComplaintAnalysisClient implements ComplaintAnalysisClient {

	private final BedrockRuntimeClient bedrockRuntimeClient;
	private final ObjectMapper objectMapper;
	private final String modelId;

	public BedrockComplaintAnalysisClient(
			BedrockRuntimeClient bedrockRuntimeClient,
			ObjectMapper objectMapper,
			@Value("${app.aws.bedrock.model-id}") String modelId
	) {
		this.bedrockRuntimeClient = bedrockRuntimeClient;
		this.objectMapper = objectMapper;
		this.modelId = modelId;
	}

	@Override
	public ComplaintAnalysisResult analyze(Complaint complaint) {
		if (modelId == null || modelId.isBlank()) {
			throw new IllegalStateException("Bedrock model id is required when app.aws.bedrock.enabled=true");
		}
		try {
			String prompt = buildPrompt(complaint);
			String requestBody = objectMapper.writeValueAsString(new AnthropicRequest(
					"bedrock-2023-05-31",
					1024,
					List.of(new Message("user", List.of(new TextContent("text", prompt))))
			));
			InvokeModelResponse response = bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
					.modelId(modelId)
					.contentType("application/json")
					.accept("application/json")
					.body(SdkBytes.fromUtf8String(requestBody))
					.build());
			JsonNode result = extractJsonObject(response.body().asUtf8String());
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
			throw new IllegalStateException("Failed to analyze complaint with Bedrock", exception);
		}
	}

	private String buildPrompt(Complaint complaint) {
		return """
				Analyze this civil complaint for a Korean local-government complaint handling system.
				Return only one JSON object with these fields:
				intent, urgency, sentiment, departmentCode, locationText, geoJson, keywords, requiredAction.

				Allowed urgency values: LOW, NORMAL, HIGH, EMERGENCY.
				Allowed sentiment values: NEUTRAL, DISCOMFORT, ANGER, ANXIETY.
				Allowed departmentCode values:
				RESOURCE_RECYCLING, ROAD, TRAFFIC, CIVIL_AFFAIRS.

				Use GeoJSON Feature format for geoJson when location text is present; otherwise return null.
				Do not include markdown fences or explanatory text.

				Complaint:
				%s

				Location:
				%s
				""".formatted(complaint.getRawText(), complaint.getLocationText()).trim();
	}

	private JsonNode extractJsonObject(String responseBody) throws Exception {
		JsonNode root = objectMapper.readTree(responseBody);
		String text = root.path("content").path(0).path("text").asText();
		if (text == null || text.isBlank()) {
			throw new IllegalStateException("Bedrock response did not contain analysis text");
		}
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new IllegalStateException("Bedrock analysis text did not contain a JSON object");
		}
		return objectMapper.readTree(text.substring(start, end + 1));
	}

	private String requiredText(JsonNode node, String fieldName) {
		String value = node.path(fieldName).asText();
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Bedrock analysis is missing field: " + fieldName);
		}
		return value;
	}

	private String textOrDefault(JsonNode node, String fieldName, String defaultValue) {
		String value = node.path(fieldName).asText();
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String textOrNull(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		return value.isTextual() ? value.asText() : value.toString();
	}

	private record AnthropicRequest(String anthropic_version, int max_tokens, List<Message> messages) {
	}

	private record Message(String role, List<TextContent> content) {
	}

	private record TextContent(String type, String text) {
	}
}
