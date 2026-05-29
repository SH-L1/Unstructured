package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.domain.Complaint;

public interface ComplaintAnalysisService {

	ComplaintAnalysisResponse analyze(Complaint complaint);
}
