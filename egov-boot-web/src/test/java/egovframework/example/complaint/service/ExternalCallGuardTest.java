package egovframework.example.complaint.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class ExternalCallGuardTest {

	@Test
	void opensCircuitAfterConfiguredFailures() {
		ExternalCallGuard guard = new ExternalCallGuard(2, 60_000, 100, Clock.systemUTC());

		assertThatThrownBy(() -> guard.execute("provider", 10, this::fail))
				.hasMessage("provider failure");
		assertThatThrownBy(() -> guard.execute("provider", 10, this::fail))
				.hasMessage("provider failure");
		assertThatThrownBy(() -> guard.execute("provider", 10, () -> "should not run"))
				.hasMessageContaining("circuit is open");
	}

	@Test
	void blocksCallsAboveCostLimit() {
		ExternalCallGuard guard = new ExternalCallGuard(2, 60_000, 100, Clock.systemUTC());

		assertThatThrownBy(() -> guard.execute("provider", 101, () -> "blocked"))
				.hasMessageContaining("cost limit exceeded");
	}

	private String fail() {
		throw new IllegalStateException("provider failure");
	}
}
