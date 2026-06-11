package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.ProcessingJob;
import egovframework.example.complaint.domain.ProcessingJobType;
import egovframework.example.complaint.domain.ProcessingJobStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

	Optional<ProcessingJob> findByJobTypeAndIdempotencyKey(ProcessingJobType jobType, String idempotencyKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.QueryHints({
			@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")
	})
	@Query("select job from ProcessingJob job where job.id = :id")
	Optional<ProcessingJob> findByIdForUpdate(@Param("id") UUID id);

	List<ProcessingJob> findByStatusAndLeaseUntilBefore(ProcessingJobStatus status, LocalDateTime now);

	List<ProcessingJob> findByStatus(ProcessingJobStatus status);

	List<ProcessingJob> findByStatusOrderByCreatedAtAsc(ProcessingJobStatus status);
}
