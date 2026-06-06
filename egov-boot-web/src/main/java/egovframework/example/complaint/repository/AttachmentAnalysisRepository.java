package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.AttachmentAnalysis;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentAnalysisRepository extends JpaRepository<AttachmentAnalysis, UUID> {

	Optional<AttachmentAnalysis> findByAttachment_Id(UUID attachmentId);

	List<AttachmentAnalysis> findByAttachment_Complaint_IdAndApprovedForAiTrueOrderByCreatedAtAsc(UUID complaintId);

	boolean existsByAttachment_Complaint_IdAndApprovedForAiFalse(UUID complaintId);
}
