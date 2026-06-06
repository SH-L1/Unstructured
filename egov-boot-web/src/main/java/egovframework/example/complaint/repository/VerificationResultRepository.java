package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.VerificationResult;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationResultRepository extends JpaRepository<VerificationResult, UUID> {

	List<VerificationResult> findByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

	boolean existsByOfficialDraftIdAndRuleCodeAndStatus(Long officialDraftId, String ruleCode, String status);
}
