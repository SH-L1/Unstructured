package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.RagContextResponse;
import egovframework.example.complaint.domain.Complaint;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MockRagSearchService implements RagSearchService {

	@Override
	public List<RagContextResponse> searchContexts(Complaint complaint) {
		return List.of(
				new RagContextResponse(
						"mock-waste-ordinance-001",
						"폐기물관리법 처리 근거",
						"LAW",
						"폐기물관리법 및 지방자치단체 폐기물 관리 조례",
						"무단투기 민원이 접수되면 담당 부서가 현장을 확인하고 수거 조치 및 위반 행위 검토 절차를 진행한다.",
						0.92
				),
				new RagContextResponse(
						"mock-civil-manual-001",
						"민원 응대 매뉴얼",
						"MANUAL",
						"민원 처리 매뉴얼",
						"민원 답변에는 접수 사실, 담당 부서, 처리 예정 절차, 추가 확인 필요 사항을 포함한다.",
						0.87
				)
		);
	}
}
