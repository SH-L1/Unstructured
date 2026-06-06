package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.DraftClaim;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftClaimRepository extends JpaRepository<DraftClaim, UUID> {

	List<DraftClaim> findByOfficialDraft_IdOrderByClaimIndexAsc(Long officialDraftId);
}
