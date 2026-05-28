package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.Complaint;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {
}
