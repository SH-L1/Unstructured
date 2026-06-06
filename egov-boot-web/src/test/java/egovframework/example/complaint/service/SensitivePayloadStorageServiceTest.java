package egovframework.example.complaint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SensitivePayloadStorageServiceTest {

	@TempDir
	Path directory;

	@Test
	void requiresStrongEnoughEncryptionKey() {
		assertThatThrownBy(() -> new SensitivePayloadStorageService(directory.toString(), "short", new ObjectMapper()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("at least 32");
	}

	@Test
	void usesRandomReferenceInsteadOfRawContentHash() {
		UUID complaintId = UUID.randomUUID();
		SensitivePayloadStorageService service = new SensitivePayloadStorageService(
				directory.toString(),
				"test-encryption-key-with-at-least-32-characters",
				new ObjectMapper()
		);

		String reference = service.storeComplaintPayload(complaintId, "sensitive raw text", "private location");

		assertThat(reference).matches(complaintId + "-[0-9a-f-]{36}\\.enc");
		assertThat(Files.exists(directory.resolve(reference))).isTrue();
	}
}
