package egovframework.example.complaint.service;

import egovframework.example.complaint.domain.ComplaintAnalysis;
import egovframework.example.complaint.domain.KnowledgeDocument;
import java.util.List;

public interface KnowledgeDocumentSearchService {

	List<KnowledgeDocument> search(ComplaintAnalysis analysis);
}
