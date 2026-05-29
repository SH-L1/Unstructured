package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.Complaint;

public interface ComplaintAnalysisClient {

	ComplaintAnalysisResult analyze(Complaint complaint);
}
