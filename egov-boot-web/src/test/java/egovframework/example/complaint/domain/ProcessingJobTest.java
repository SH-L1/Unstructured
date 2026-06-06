package egovframework.example.complaint.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProcessingJobTest {

	@Test
	void retriesFailedJobUpToConfiguredLimit() {
		ProcessingJob job = new ProcessingJob(complaint(), ProcessingJobType.RETRIEVE, "retry-key", 2);

		job.start(LocalDateTime.now().plusMinutes(1));
		job.fail("temporary", false);
		job.start(LocalDateTime.now().plusMinutes(1));
		job.fail("temporary", false);

		assertThat(job.getAttempts()).isEqualTo(2);
		assertThatThrownBy(() -> job.start(LocalDateTime.now().plusMinutes(1)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("retry limit");
	}

	@Test
	void expiredLeaseReturnsRunningJobToFailedState() {
		ProcessingJob job = new ProcessingJob(complaint(), ProcessingJobType.VERIFY, "lease-key", 3);
		LocalDateTime now = LocalDateTime.now();
		job.start(now.minusSeconds(1));

		assertThat(job.expireLease(now)).isTrue();
		assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
		assertThat(job.getFailureReason()).contains("lease expired");
	}

	@Test
	void expiredLeaseOnLastAttemptBlocksJob() {
		ProcessingJob job = new ProcessingJob(complaint(), ProcessingJobType.VERIFY, "last-lease-key", 1);
		LocalDateTime now = LocalDateTime.now();
		job.start(now.minusSeconds(1));

		assertThat(job.expireLease(now)).isTrue();
		assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.BLOCKED);
	}

	@Test
	void retainsRetryNotBeforeForRetryableFailure() {
		ProcessingJob job = new ProcessingJob(complaint(), ProcessingJobType.CLASSIFY_ISSUES, "worker-retry-key", 3);
		LocalDateTime retryAt = LocalDateTime.now().plusSeconds(5);
		job.start(LocalDateTime.now().plusMinutes(1));

		job.fail("provider unavailable", false, retryAt);

		assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
		assertThat(job.getLeaseUntil()).isEqualTo(retryAt);
	}

	private Complaint complaint() {
		return new Complaint(SourceChannel.WEB, "redacted", "redacted", "Pilot road");
	}
}
