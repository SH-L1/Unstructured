package com.school.complaint.repository;

import com.school.complaint.domain.RagContext;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagContextRepository extends JpaRepository<RagContext, Long> {

	List<RagContext> findByComplaintIdOrderByScoreDesc(UUID complaintId);

	List<RagContext> findByOfficialDraftIdOrderByScoreDesc(Long officialDraftId);
}
