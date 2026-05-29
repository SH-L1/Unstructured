package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.ComplaintAnalysis;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintAnalysisRepository extends JpaRepository<ComplaintAnalysis, Long> {

	Optional<ComplaintAnalysis> findByComplaintId(UUID complaintId);
}
