package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.RetrievalRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrievalRunRepository extends JpaRepository<RetrievalRun, UUID> {

	Optional<RetrievalRun> findFirstByComplaintIdOrderByCreatedAtDesc(UUID complaintId);
}
