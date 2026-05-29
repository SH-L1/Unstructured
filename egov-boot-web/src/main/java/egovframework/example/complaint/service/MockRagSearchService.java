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
						"Waste Management Act and local waste handling ordinance",
						"After receiving a waste dumping complaint, the department checks the site, removes waste and reviews whether enforcement action is needed.",
						0.92
				),
				new RagContextResponse(
						"mock-civil-manual-001",
						"Civil complaint response manual",
						"A response should include the received facts, expected processing steps, responsible department and any additional confirmation required.",
						0.87
				)
		);
	}
}
