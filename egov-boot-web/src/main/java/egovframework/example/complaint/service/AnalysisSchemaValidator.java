package egovframework.example.complaint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AnalysisSchemaValidator {

	private static final Set<String> ROOT_FIELDS = Set.of(
			"schemaVersion", "intent", "urgency", "sentiment", "departmentCode", "locationText",
			"keywords", "requiredAction", "issues"
	);
	private static final Set<String> ISSUE_FIELDS = Set.of(
			"summary", "complaintType", "jurisdictionStatus", "safetyRisk", "expressionRisk",
			"processability", "departmentCandidates", "locationCandidates", "evidenceIds"
	);
	private static final Set<String> URGENCIES = Set.of("LOW", "NORMAL", "HIGH", "EMERGENCY");
	private static final Set<String> SENTIMENTS = Set.of("NEUTRAL", "DISCOMFORT", "ANGER", "ANXIETY");
	private static final Set<String> DEPARTMENTS = Set.of(
			"SAFETY_CONTROL", "RESOURCE_RECYCLING", "ROAD", "TRAFFIC", "CIVIL_AFFAIRS",
			"ENVIRONMENT", "BUILDING_HOUSING", "PARK_GREEN", "WATER_SEWER",
			"HEALTH_SANITATION", "ANIMAL_LIVESTOCK", "URBAN_MANAGEMENT", "WELFARE"
	);
	private static final Set<String> COMPLAINT_TYPES = Set.of(
			"ILLEGAL_DUMPING", "ROAD_DAMAGE", "ILLEGAL_PARKING", "TRAFFIC_SIGN",
			"NOISE", "ENVIRONMENT", "HAZARDOUS_MATERIAL", "GENERAL"
	);
	private static final Set<String> JURISDICTIONS = Set.of("ASAN_CANDIDATE", "NEEDS_JURISDICTION");
	private static final Set<String> SAFETY_RISKS = Set.of("NORMAL", "HIGH", "EMERGENCY");
	private static final Set<String> EXPRESSION_RISKS = Set.of("NORMAL", "HIGH");
	private static final Set<String> PROCESSABILITY = Set.of(
			"PROCESSABLE", "NEEDS_LOCATION", "NEEDS_JURISDICTION", "NEEDS_REVIEW"
	);
	private static final Set<String> FORBIDDEN_SPATIAL_FIELDS = Set.of(
			"geojson", "coordinates", "latitude", "longitude", "lat", "lon", "lng"
	);
	private static final Pattern COORDINATE_PAIR = Pattern.compile(
			"(?<!\\d)[+-]?\\d{1,3}\\.\\d{3,}\\s*,\\s*[+-]?\\d{1,3}\\.\\d{3,}(?!\\d)"
	);

	private final ObjectMapper objectMapper;

	public AnalysisSchemaValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void validate(ComplaintAnalysisResult result) {
		if (result.geoJson() != null && !result.geoJson().isBlank()) {
			throw new IllegalStateException("AI analysis must not include GeoJSON or coordinates");
		}
		try {
			JsonNode root = StrictJson.readSingleDocument(objectMapper, result.analysisJson(), "AI analysis");
			requireObject(root, "analysis");
			requireExactFields(root, ROOT_FIELDS, "analysis");
			if (!"complaint-support-v1".equals(root.path("schemaVersion").asText())) {
				throw new IllegalStateException("Analysis schemaVersion must be complaint-support-v1");
			}
			requireBoundedText(root, "intent", 100);
			requireAllowed(root, "urgency", URGENCIES);
			requireAllowed(root, "sentiment", SENTIMENTS);
			requireAllowed(root, "departmentCode", DEPARTMENTS);
			requireNullableText(root, "locationText", 500);
			requireTextArray(root, "keywords", 30, 200);
			requireBoundedText(root, "requiredAction", 1_000);
			requireResultMatch(root, "intent", result.intent());
			requireResultMatch(root, "urgency", result.urgency());
			requireResultMatch(root, "sentiment", result.sentiment());
			requireResultMatch(root, "departmentCode", result.departmentCode());
			requireNullableResultMatch(root, "locationText", result.locationText());
			JsonNode issues = root.path("issues");
			if (!issues.isArray() || issues.isEmpty() || issues.size() > 10) {
				throw new IllegalStateException("AI analysis issues must contain between 1 and 10 items");
			}
			for (JsonNode issue : issues) {
				requireObject(issue, "issue");
				requireExactFields(issue, ISSUE_FIELDS, "issue");
				requireBoundedText(issue, "summary", 500);
				requireAllowed(issue, "complaintType", COMPLAINT_TYPES);
				requireAllowed(issue, "jurisdictionStatus", JURISDICTIONS);
				requireAllowed(issue, "safetyRisk", SAFETY_RISKS);
				requireAllowed(issue, "expressionRisk", EXPRESSION_RISKS);
				requireAllowed(issue, "processability", PROCESSABILITY);
				requireTextArray(issue, "departmentCandidates", 10, 80);
				requireTextArray(issue, "locationCandidates", 10, 500);
				requireTextArray(issue, "evidenceIds", 50, 200);
			}
			rejectSpatialFields(root);
			rejectCoordinateValues(root);
		}
		catch (IllegalStateException exception) {
			throw new IllegalStateException("Analysis schema validation failed: " + exception.getMessage(), exception);
		}
		catch (Exception exception) {
			throw new IllegalStateException(
					"Analysis schema validation failed: AI analysis does not match complaint-support-v1", exception
			);
		}
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
			throw new IllegalStateException(name + " fields do not match complaint-support-v1 schema");
		}
	}

	private void requireText(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isTextual() || value.asText().isBlank()) {
			throw new IllegalStateException(field + " must be a non-empty string");
		}
	}

	private void requireBoundedText(JsonNode node, String field, int maxLength) {
		requireText(node, field);
		if (node.path(field).asText().length() > maxLength) {
			throw new IllegalStateException(field + " exceeds the maximum length");
		}
	}

	private void requireNullableText(JsonNode node, String field, int maxLength) {
		JsonNode value = node.path(field);
		if (!value.isNull() && (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength)) {
			throw new IllegalStateException(field + " must be a non-empty string or null");
		}
	}

	private void requireAllowed(JsonNode node, String field, Set<String> allowed) {
		requireText(node, field);
		if (!allowed.contains(node.path(field).asText())) {
			throw new IllegalStateException(field + " contains an unsupported value");
		}
	}

	private void requireTextArray(JsonNode node, String field, int maxItems, int maxItemLength) {
		JsonNode value = node.path(field);
		if (!value.isArray() || value.size() > maxItems) {
			throw new IllegalStateException(field + " must be an array");
		}
		for (JsonNode item : value) {
			if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > maxItemLength) {
				throw new IllegalStateException(field + " must contain only non-empty strings");
			}
		}
	}

	private void requireResultMatch(JsonNode node, String field, String resultValue) {
		if (!Objects.equals(node.path(field).asText(), resultValue)) {
			throw new IllegalStateException(field + " result does not match the validated JSON schema output");
		}
	}

	private void requireNullableResultMatch(JsonNode node, String field, String resultValue) {
		JsonNode value = node.path(field);
		String schemaValue = value.isNull() ? null : value.asText();
		if (!Objects.equals(schemaValue, resultValue)) {
			throw new IllegalStateException(field + " result does not match the validated JSON schema output");
		}
	}

	private void rejectSpatialFields(JsonNode node) {
		if (node.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();
				if (FORBIDDEN_SPATIAL_FIELDS.contains(entry.getKey().toLowerCase())) {
					throw new IllegalStateException("AI analysis must not include spatial coordinates");
				}
				rejectSpatialFields(entry.getValue());
			}
		}
		else if (node.isArray()) {
			node.forEach(this::rejectSpatialFields);
		}
	}

	private void rejectCoordinateValues(JsonNode node) {
		if (node.isTextual() && COORDINATE_PAIR.matcher(node.asText()).find()) {
			throw new IllegalStateException("AI analysis must not include spatial coordinate values");
		}
		if (node.isContainerNode()) {
			node.forEach(this::rejectCoordinateValues);
		}
	}
}
