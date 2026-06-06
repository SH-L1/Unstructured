package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.WorkflowAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowAuditEventRepository extends JpaRepository<WorkflowAuditEvent, UUID> {
}
