package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.KnowledgeDocument;
import java.util.List;

public interface DraftGenerationClient {

	String generateDraft(
			Complaint complaint,
			ComplaintAnalysis analysis,
			List<KnowledgeDocument> documents,
			List<String> approvedAttachmentTexts
	);
}
