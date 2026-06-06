package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.RagContext;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface RagContextRepository extends JpaRepository<RagContext, Long> {

	List<RagContext> findByComplaintIdOrderByIdAsc(UUID complaintId);

	@EntityGraph(attributePaths = {"knowledgeDocument", "knowledgeDocumentChunk"})
	List<RagContext> findByOfficialDraftIdOrderByIdAsc(Long officialDraftId);
}
