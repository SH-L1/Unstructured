package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.EvidenceSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceSnapshotRepository extends JpaRepository<EvidenceSnapshot, UUID> {

	List<EvidenceSnapshot> findByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

	List<EvidenceSnapshot> findByRetrievalRunIdOrderByCreatedAtDesc(UUID retrievalRunId);

	@Query(value = """
			select distinct e.*
			from evidence_snapshots e
			join claim_evidence_links l on l.evidence_snapshot_id = e.id
			join draft_claims c on c.id = l.draft_claim_id
			where c.official_draft_id = :draftId
			order by e.created_at desc
			""", nativeQuery = true)
	List<EvidenceSnapshot> findForDraft(@Param("draftId") Long draftId);
}
