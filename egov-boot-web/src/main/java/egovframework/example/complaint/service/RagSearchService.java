package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.RagContextResponse;
import egovframework.example.complaint.domain.Complaint;
import java.util.List;

public interface RagSearchService {

	List<RagContextResponse> searchContexts(Complaint complaint);
}
