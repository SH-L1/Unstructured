package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.AiRun;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRunRepository extends JpaRepository<AiRun, UUID> {

	List<AiRun> findByComplaintIdOrderByCreatedAtDesc(UUID complaintId);
}
