package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.Complaint;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {

	boolean existsByRawText(String rawText);
}
