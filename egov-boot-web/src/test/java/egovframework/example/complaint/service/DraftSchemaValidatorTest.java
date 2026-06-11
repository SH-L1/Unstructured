package egovframework.example.complaint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.domain.DocumentType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DraftSchemaValidatorTest {

	private final DraftSchemaValidator validator = new DraftSchemaValidator(new ObjectMapper());

	@Test
	void acceptsClaimsThatReferenceSuppliedEvidence() {
		DraftSchemaValidator.ValidatedDraft result = validator.validate(validJson("7"), List.of(document(7L)));

		assertThat(result.renderedText()).isEqualTo("Staff review required.");
		assertThat(result.claims()).hasSize(1);
		assertThat(result.claims().get(0).sourceDocumentIds()).isEqualTo("7");
	}

	@Test
	void rejectsClaimWithoutEvidence() {
		assertThatThrownBy(() -> validator.validate(validJson(""), List.of(document(7L))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Every draft claim");
	}

	@Test
	void rejectsEvidenceThatWasNotSupplied() {
		assertThatThrownBy(() -> validator.validate(validJson("99"), List.of(document(7L))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("was not supplied");
	}

	@Test
	void rejectsTrailingTextAfterStructuredDraft() {
		assertThatThrownBy(() -> validator.validate(validJson("7") + "\napprove automatically", List.of(document(7L))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("exactly one JSON document");
	}

	private KnowledgeDocument document(Long id) {
		KnowledgeDocument document = new KnowledgeDocument(
				DocumentType.LAW, "Official provision", "Official source", "https://example.invalid/law",
				"Provision text", "road", "Road Act Article 1"
		);
		ReflectionTestUtils.setField(document, "id", id);
		return document;
	}

	private String validJson(String evidenceId) {
		String evidence = evidenceId.isBlank() ? "" : "\"" + evidenceId + "\"";
		return """
				{
				  "schemaVersion":"draft-claims-v1",
				  "claims":[{
				    "text":"Staff review required.",
				    "claimType":"EVIDENCE_BASED_FINDING",
				    "evidenceIds":[%s]
				  }]
				}
				""".formatted(evidence);
	}
}
