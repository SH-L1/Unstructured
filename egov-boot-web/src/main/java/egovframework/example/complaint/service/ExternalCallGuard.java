package egovframework.example.complaint.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExternalCallGuard {

	private final int failureThreshold;
	private final long openDurationMillis;
	private final long maxCostUnitsPerCall;
	private final Clock clock;
	private final Map<String, CircuitState> states = new ConcurrentHashMap<>();
	private final Map<String, Object> locks = new ConcurrentHashMap<>();

	@Autowired
	public ExternalCallGuard(
			@Value("${app.external.circuit-breaker.failure-threshold:3}") int failureThreshold,
			@Value("${app.external.circuit-breaker.open-duration-ms:60000}") long openDurationMillis,
			@Value("${app.external.max-cost-units-per-call:2000}") long maxCostUnitsPerCall
	) {
		this(failureThreshold, openDurationMillis, maxCostUnitsPerCall, Clock.systemUTC());
	}

	ExternalCallGuard(int failureThreshold, long openDurationMillis, long maxCostUnitsPerCall, Clock clock) {
		if (failureThreshold < 1 || openDurationMillis < 1 || maxCostUnitsPerCall < 0) {
			throw new IllegalArgumentException("External call guard limits must be positive");
		}
		this.failureThreshold = failureThreshold;
		this.openDurationMillis = openDurationMillis;
		this.maxCostUnitsPerCall = maxCostUnitsPerCall;
		this.clock = clock;
	}

	public <T> T execute(String provider, long estimatedCostUnits, Supplier<T> call) {
		if (estimatedCostUnits < 0 || estimatedCostUnits > maxCostUnitsPerCall) {
			throw new IllegalStateException("External call cost limit exceeded for " + provider);
		}
		Object lock = locks.computeIfAbsent(provider, ignored -> new Object());
		CircuitState state = states.computeIfAbsent(provider, ignored -> new CircuitState());
		synchronized (lock) {
			if (state.openUntil != null && clock.instant().isBefore(state.openUntil)) {
				throw new IllegalStateException("External provider circuit is open: " + provider);
			}
			if (state.openUntil != null) {
				state.openUntil = null;
				state.failures = 0;
			}
		}
		try {
			T result = call.get();
			synchronized (lock) {
				state.failures = 0;
				state.openUntil = null;
			}
			return result;
		}
		catch (RuntimeException exception) {
			synchronized (lock) {
				state.failures++;
				if (state.failures >= failureThreshold) {
					state.openUntil = Instant.now(clock).plusMillis(openDurationMillis);
				}
			}
			throw exception;
		}
	}

	private static final class CircuitState {
		private int failures;
		private Instant openUntil;
	}
}
