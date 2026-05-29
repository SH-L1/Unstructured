package egovframework.example.complaint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.KnowledgeDocument;
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
public class BedrockDraftGenerationClient implements DraftGenerationClient {

	private final BedrockRuntimeClient bedrockRuntimeClient;
	private final ObjectMapper objectMapper;
	private final String modelId;

	public BedrockDraftGenerationClient(
			BedrockRuntimeClient bedrockRuntimeClient,
			ObjectMapper objectMapper,
			@Value("${app.aws.bedrock.model-id}") String modelId
	) {
		this.bedrockRuntimeClient = bedrockRuntimeClient;
		this.objectMapper = objectMapper;
		this.modelId = modelId;
	}

	@Override
	public String generateDraft(Complaint complaint, ComplaintAnalysis analysis, List<KnowledgeDocument> documents) {
		if (modelId == null || modelId.isBlank()) {
			throw new IllegalStateException("Bedrock model id is required when app.aws.bedrock.enabled=true");
		}
		try {
			String prompt = buildPrompt(complaint, analysis, documents);
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
			JsonNode root = objectMapper.readTree(response.body().asUtf8String());
			JsonNode text = root.path("content").path(0).path("text");
			if (!text.isTextual() || text.asText().isBlank()) {
				throw new IllegalStateException("Bedrock response did not contain draft text");
			}
			return text.asText().trim();
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to generate draft with Bedrock", exception);
		}
	}

	private String buildPrompt(Complaint complaint, ComplaintAnalysis analysis, List<KnowledgeDocument> documents) {
		StringBuilder context = new StringBuilder();
		for (KnowledgeDocument document : documents) {
			context.append("- ")
					.append(document.getTitle())
					.append(" / ")
					.append(document.getLegalBasis())
					.append(": ")
					.append(document.getContent())
					.append("\n");
		}
		return """
				You are drafting a formal civil complaint response for a public office.
				Write a concise, polite draft response for staff review. Do not invent legal bases.

				Complaint:
				%s

				Location:
				%s

				Analysis:
				Intent: %s
				Urgency: %s
				Sentiment: %s
				Responsible department: %s

				RAG references:
				%s
				""".formatted(
				complaint.getRawText(),
				complaint.getLocationText(),
				analysis.getIntent(),
				analysis.getUrgency(),
				analysis.getSentiment(),
				analysis.getDepartment().getName(),
				context
		).trim();
	}

	private record AnthropicRequest(String anthropic_version, int max_tokens, List<Message> messages) {
	}

	private record Message(String role, List<TextContent> content) {
	}

	private record TextContent(String type, String text) {
	}
}
