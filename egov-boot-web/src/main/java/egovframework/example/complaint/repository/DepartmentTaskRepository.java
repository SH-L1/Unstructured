package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.DepartmentTask;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentTaskRepository extends JpaRepository<DepartmentTask, UUID> {

	List<DepartmentTask> findByIssue_IdOrderByCreatedAtAsc(UUID issueId);
}
