package egovframework.example.complaint.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSearchKnowledgeDocumentSearchServiceTest {

	@Test
	void usesPurposeSpecificIndicesAndExcludesLegacyKnowledge() {
		assertThat(OpenSearchKnowledgeDocumentSearchService.purposeIndexNames("pilot-knowledge"))
				.containsExactly(
						"pilot-knowledge-official-law",
						"pilot-knowledge-procedure",
						"pilot-knowledge-historical-case",
						"pilot-knowledge-style",
						"pilot-knowledge-local-ordinance-reference",
						"pilot-knowledge-style-reference",
						"pilot-knowledge-organization-routing",
						"pilot-knowledge-evaluation-training"
				)
				.doesNotContain("pilot-knowledge-unverified-legacy");
	}
}
