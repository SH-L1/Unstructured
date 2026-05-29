package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.DraftRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftRevisionRepository extends JpaRepository<DraftRevision, Long> {
}
