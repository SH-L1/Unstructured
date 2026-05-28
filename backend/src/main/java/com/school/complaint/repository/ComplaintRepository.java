package com.school.complaint.repository;

import com.school.complaint.domain.Complaint;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {
}
