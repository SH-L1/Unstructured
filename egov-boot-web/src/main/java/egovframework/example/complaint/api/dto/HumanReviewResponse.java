package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.HumanReview;
import java.time.LocalDateTime;

public record HumanReviewResponse(
		String action,
		String actor,
		String actorRole,
		String notes,
		LocalDateTime createdAt
) {
	public static HumanReviewResponse from(HumanReview review) {
		return new HumanReviewResponse(
				review.getAction(),
				review.getActor(),
				review.getActorRole(),
				review.getNotes(),
				review.getCreatedAt()
		);
	}
}
