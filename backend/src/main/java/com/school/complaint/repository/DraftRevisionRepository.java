package com.school.complaint.repository;

import com.school.complaint.domain.DraftRevision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftRevisionRepository extends JpaRepository<DraftRevision, Long> {

	List<DraftRevision> findByOfficialDraftIdOrderByCreatedAtDesc(Long officialDraftId);
}
