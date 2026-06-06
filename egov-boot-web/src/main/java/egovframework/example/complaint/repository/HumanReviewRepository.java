package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.HumanReview;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HumanReviewRepository extends JpaRepository<HumanReview, UUID> {

	Optional<HumanReview> findByIdempotencyKey(String idempotencyKey);

	List<HumanReview> findByComplaintIdOrderByCreatedAtDesc(UUID complaintId);
}
