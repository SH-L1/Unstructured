package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.DraftResponse;
import egovframework.example.complaint.api.dto.RagContextResponse;
import egovframework.example.complaint.domain.Complaint;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MockDraftService implements DraftService {

	private final RagSearchService ragSearchService;

	public MockDraftService(RagSearchService ragSearchService) {
		this.ragSearchService = ragSearchService;
	}

	@Override
	public DraftResponse generateDraft(Complaint complaint, ComplaintAnalysisResponse analysis) {
		List<RagContextResponse> references = ragSearchService.searchContexts(complaint);
		String draftText = """
				Hello. We have received your complaint and classified it as: %s.
				The responsible department is expected to be %s. The department will review the submitted details and take follow-up action according to the relevant procedure.
				If additional confirmation is needed during the review, we will contact you separately.
				""".formatted(analysis.intent(), analysis.department()).trim();
		return new DraftResponse(null, complaint.getId(), draftText, "mock-bedrock-illegal-dumping-v1", "DRAFT", references);
	}

	@Override
	public DraftResponse updateDraft(Complaint complaint, String draftText) {
		return new DraftResponse(null, complaint.getId(), draftText, "mock-bedrock-illegal-dumping-v1", "REVISED", ragSearchService.searchContexts(complaint));
	}
}
