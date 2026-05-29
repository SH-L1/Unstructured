package com.school.complaint.repository;

import com.school.complaint.domain.OfficialDraft;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfficialDraftRepository extends JpaRepository<OfficialDraft, Long> {

	List<OfficialDraft> findByComplaintIdOrderByCreatedAtDesc(UUID complaintId);
}
