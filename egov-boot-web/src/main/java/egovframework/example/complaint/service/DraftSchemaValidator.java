package egovframework.example.complaint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.domain.KnowledgeDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DraftSchemaValidator {

	private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "claims");
	private static final Set<String> CLAIM_FIELDS = Set.of("text", "claimType", "evidenceIds");
	private static final Set<String> CLAIM_TYPES = Set.of(
			"ACKNOWLEDGEMENT", "EVIDENCE_BASED_FINDING", "PROPOSED_NEXT_STEP", "REVIEW_NOTICE"
	);

	private final ObjectMapper objectMapper;

	public DraftSchemaValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ValidatedDraft validate(String value, List<KnowledgeDocument> documents) {
		try {
			JsonNode root = StrictJson.readSingleDocument(objectMapper, value, "AI draft");
			requireObject(root, "draft");
			requireExactFields(root, ROOT_FIELDS, "draft");
			if (!"draft-claims-v1".equals(root.path("schemaVersion").asText())) {
				throw new IllegalStateException("Draft schemaVersion must be draft-claims-v1");
			}
			Set<String> allowedEvidenceIds = documents.stream()
					.map(document -> String.valueOf(document.getId()))
					.collect(Collectors.toSet());
			JsonNode claimsNode = root.path("claims");
			if (!claimsNode.isArray() || claimsNode.isEmpty() || claimsNode.size() > 50) {
				throw new IllegalStateException("Draft claims must contain between 1 and 50 items");
			}
			List<ValidatedClaim> claims = new ArrayList<>();
			for (JsonNode claimNode : claimsNode) {
				requireObject(claimNode, "claim");
				requireExactFields(claimNode, CLAIM_FIELDS, "claim");
				String text = requireBoundedText(claimNode, "text", 2_000);
				String claimType = requireBoundedText(claimNode, "claimType", 40);
				if (!CLAIM_TYPES.contains(claimType)) {
					throw new IllegalStateException("Draft claimType contains an unsupported value");
				}
				boolean allowEmpty = "ACKNOWLEDGEMENT".equals(claimType) || "REVIEW_NOTICE".equals(claimType);
				List<String> evidenceIds = requireEvidenceIds(claimNode.path("evidenceIds"), allowedEvidenceIds, allowEmpty);
				claims.add(new ValidatedClaim(text, claimType, evidenceIds));
			}
			return new ValidatedDraft(
					claims.stream().map(ValidatedClaim::text).collect(Collectors.joining("\n")),
					List.copyOf(claims)
			);
		}
		catch (IllegalStateException exception) {
			throw new IllegalStateException("Draft schema validation failed: " + exception.getMessage(), exception);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Draft schema validation failed: AI draft does not match draft-claims-v1", exception);
		}
	}

	private List<String> requireEvidenceIds(JsonNode node, Set<String> allowedEvidenceIds, boolean allowEmpty) {
		if (!node.isArray() || node.size() > 20) {
			throw new IllegalStateException("Every draft claim must reference between 1 and 20 evidence IDs");
		}
		if (node.isEmpty()) {
			if (allowEmpty) {
				return List.of();
			} else {
				throw new IllegalStateException("Every draft claim must reference between 1 and 20 evidence IDs");
			}
		}
		Set<String> result = new LinkedHashSet<>();
		for (JsonNode item : node) {
			if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 200) {
				throw new IllegalStateException("Draft evidenceIds must contain bounded non-empty strings");
			}
			if (!allowedEvidenceIds.contains(item.asText())) {
				throw new IllegalStateException("Draft claim references evidence that was not supplied");
			}
			result.add(item.asText());
		}
		return List.copyOf(result);
	}

	private String requireBoundedText(JsonNode node, String field, int maxLength) {
		JsonNode value = node.path(field);
		if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
			throw new IllegalStateException(field + " must be a bounded non-empty string");
		}
		return value.asText().trim();
	}

	private void requireObject(JsonNode node, String name) {
		if (!node.isObject()) {
			throw new IllegalStateException(name + " must be a JSON object");
		}
	}

	private void requireExactFields(JsonNode node, Set<String> expected, String name) {
		Set<String> actual = new HashSet<>();
		node.fieldNames().forEachRemaining(actual::add);
		if (!actual.equals(expected)) {
			throw new IllegalStateException(name + " fields do not match draft-claims-v1 schema");
		}
	}

	public record ValidatedDraft(String renderedText, List<ValidatedClaim> claims) {
	}

	public record ValidatedClaim(String text, String claimType, List<String> evidenceIds) {
		public String sourceDocumentIds() {
			return String.join(",", evidenceIds);
		}
	}
}
