package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.LocationCandidate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationCandidateRepository extends JpaRepository<LocationCandidate, UUID> {

	List<LocationCandidate> findByIssue_IdOrderByCreatedAtAsc(UUID issueId);
}
