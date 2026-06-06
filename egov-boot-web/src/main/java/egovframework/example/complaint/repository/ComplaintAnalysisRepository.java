package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.ComplaintAnalysis;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ComplaintAnalysisRepository extends JpaRepository<ComplaintAnalysis, Long> {

	@EntityGraph(attributePaths = {"department", "complaint"})
	Optional<ComplaintAnalysis> findByComplaintId(UUID complaintId);
}
