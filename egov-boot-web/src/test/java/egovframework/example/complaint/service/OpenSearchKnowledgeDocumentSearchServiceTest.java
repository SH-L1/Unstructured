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
						"pilot-knowledge-style"
				)
				.doesNotContain("pilot-knowledge-unverified-legacy");
	}
}
