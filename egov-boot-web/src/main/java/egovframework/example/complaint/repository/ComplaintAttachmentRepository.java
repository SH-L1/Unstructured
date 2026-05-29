package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.ComplaintAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintAttachmentRepository extends JpaRepository<ComplaintAttachment, UUID> {

	List<ComplaintAttachment> findByComplaintIdOrderByCreatedAtDesc(UUID complaintId);
}
