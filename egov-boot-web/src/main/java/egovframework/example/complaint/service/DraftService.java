package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.DraftResponse;
import egovframework.example.complaint.domain.Complaint;

public interface DraftService {

	DraftResponse generateDraft(Complaint complaint, ComplaintAnalysisResponse analysis);

	DraftResponse updateDraft(Complaint complaint, String draftText);
}
