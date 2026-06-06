package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.ComplaintIssue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintIssueRepository extends JpaRepository<ComplaintIssue, UUID> {

	List<ComplaintIssue> findByComplaint_IdOrderByIssueIndexAsc(UUID complaintId);

	Optional<ComplaintIssue> findByComplaint_IdAndIssueIndex(UUID complaintId, int issueIndex);
}
