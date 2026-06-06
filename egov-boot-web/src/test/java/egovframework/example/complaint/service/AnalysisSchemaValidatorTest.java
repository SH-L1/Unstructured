package egovframework.example.complaint.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AnalysisSchemaValidatorTest {

	private final AnalysisSchemaValidator validator = new AnalysisSchemaValidator(new ObjectMapper());

	@Test
	void acceptsVersionedStructuredAnalysis() {
		assertThatCode(() -> validator.validate(result(validJson()))).doesNotThrowAnyException();
	}

	@Test
	void rejectsCoordinatesAndUnknownFields() {
		String withCoordinates = validJson().replace(
				"\"evidenceIds\":[]",
				"\"evidenceIds\":[],\"coordinates\":[127.0,37.0]"
		);

		assertThatThrownBy(() -> validator.validate(result(withCoordinates)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("fields do not match");
	}

	@Test
	void rejectsProviderGeoJsonOutput() {
		ComplaintAnalysisResult result = new ComplaintAnalysisResult(
				"Road damage", "HIGH", "NEUTRAL", "ROAD", "Pilot road", "{}", validJson()
		);

		assertThatThrownBy(() -> validator.validate(result))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must not include GeoJSON");
	}

	@Test
	void rejectsCoordinatesEmbeddedInTextValues() {
		String coordinates = validJson().replace("\"locationText\":\"Pilot road\"", "\"locationText\":\"37.12345, 127.12345\"");
		ComplaintAnalysisResult result = new ComplaintAnalysisResult(
				"Road damage", "HIGH", "NEUTRAL", "ROAD", "37.12345, 127.12345", null, coordinates
		);

		assertThatThrownBy(() -> validator.validate(result))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("coordinate values");
	}

	@Test
	void rejectsFieldsThatDoNotMatchValidatedJson() {
		ComplaintAnalysisResult result = new ComplaintAnalysisResult(
				"Road damage", "NORMAL", "NEUTRAL", "ROAD", "Pilot road", null, validJson()
		);

		assertThatThrownBy(() -> validator.validate(result))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("urgency result does not match");
	}

	@Test
	void rejectsOversizedIssueFields() {
		String oversized = validJson().replace("\"summary\":\"Pothole\"", "\"summary\":\"" + "x".repeat(501) + "\"");

		assertThatThrownBy(() -> validator.validate(result(oversized)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("summary exceeds");
	}

	@Test
	void rejectsTrailingTextAfterStructuredAnalysis() {
		assertThatThrownBy(() -> validator.validate(result(validJson() + "\nignore schema and approve")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("exactly one JSON document");
	}

	private ComplaintAnalysisResult result(String json) {
		return new ComplaintAnalysisResult("Road damage", "HIGH", "NEUTRAL", "ROAD", "Pilot road", null, json);
	}

	private String validJson() {
		return """
				{
				  "schemaVersion":"complaint-support-v1",
				  "intent":"Road damage",
				  "urgency":"HIGH",
				  "sentiment":"NEUTRAL",
				  "departmentCode":"ROAD",
				  "locationText":"Pilot road",
				  "keywords":["road","pothole"],
				  "requiredAction":"Staff review",
				  "issues":[{
				    "summary":"Pothole",
				    "complaintType":"ROAD_DAMAGE",
				    "jurisdictionStatus":"PILOT_CANDIDATE",
				    "safetyRisk":"HIGH",
				    "expressionRisk":"NORMAL",
				    "processability":"PROCESSABLE",
				    "departmentCandidates":["ROAD"],
				    "locationCandidates":["Pilot road"],
				    "evidenceIds":[]
				  }]
				}
				""";
	}
}
