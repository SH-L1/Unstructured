package egovframework.example.complaint.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OfficialDraftTest {

	@Test
	void reviewerCannotApproveSameDraft() {
		Complaint complaint = new Complaint(SourceChannel.WEB, "redacted", "redacted", "Pilot road");
		OfficialDraft draft = new OfficialDraft(complaint, "Review required", "mock");
		draft.markReviewed("same-person", "reviewed");

		assertThatThrownBy(() -> draft.approve("same-person", "approve"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Reviewer cannot make");
		assertThatThrownBy(() -> draft.rejectFromApproval("same-person", "reject"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Reviewer cannot make");
	}

	@Test
	void reviewerCannotRejectDraftAfterItPassedReview() {
		Complaint complaint = new Complaint(SourceChannel.WEB, "redacted", "redacted", "Pilot road");
		OfficialDraft draft = new OfficialDraft(complaint, "Review required", "mock");
		draft.markReviewed("reviewer", "reviewed");

		assertThatThrownBy(() -> draft.rejectFromReview("too late"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("awaiting review");
	}

	@Test
	void approverCannotRejectDraftBeforeReview() {
		Complaint complaint = new Complaint(SourceChannel.WEB, "redacted", "redacted", "Pilot road");
		OfficialDraft draft = new OfficialDraft(complaint, "Review required", "mock");

		assertThatThrownBy(() -> draft.rejectFromApproval("approver", "not reviewed"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("awaiting approval");
	}
}
