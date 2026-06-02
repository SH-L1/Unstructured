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
				안녕하십니까. 귀하께서 접수하신 민원은 "%s" 유형으로 확인되었습니다.
				해당 민원은 %s에서 담당 검토할 예정이며, 접수 내용과 위치 정보를 바탕으로 현장 확인 필요 여부를 판단하겠습니다.
				검토 과정에서 추가 확인이 필요한 사항이 있으면 별도로 안내드리겠습니다.
				""".formatted(analysis.intent(), analysis.department()).trim();
		return new DraftResponse(null, complaint.getId(), draftText, "mock-korean-civil-complaint-v1", "DRAFT", references);
	}

	@Override
	public DraftResponse updateDraft(Complaint complaint, String draftText) {
		return new DraftResponse(null, complaint.getId(), draftText, "mock-korean-civil-complaint-v1", "REVISED", ragSearchService.searchContexts(complaint));
	}
}
