package egovframework.example.complaint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.complaint.repository.ComplaintAttachmentRepository;
import egovframework.example.complaint.repository.ComplaintSensitivePayloadRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageOrphanCleanupServiceTest {

	@TempDir
	Path directory;

	@Test
	void removesOnlyOldUnreferencedObjects() throws Exception {
		Path attachmentDirectory = directory.resolve("attachments");
		Path sensitiveDirectory = directory.resolve("sensitive");
		LocalFileStorageService attachments = new LocalFileStorageService(attachmentDirectory.toString());
		SensitivePayloadStorageService sensitive = new SensitivePayloadStorageService(
				sensitiveDirectory.toString(),
				"test-sensitive-storage-key-at-least-32-characters",
				new ObjectMapper()
		);
		StoredFile keptAttachment = attachments.store(
				"kept.txt", "text/plain", 4, new ByteArrayInputStream("kept".getBytes())
		);
		StoredFile orphanAttachment = attachments.store(
				"orphan.txt", "text/plain", 6, new ByteArrayInputStream("orphan".getBytes())
		);
		String keptSensitive = sensitive.storeComplaintPayload(UUID.randomUUID(), "kept", "location");
		String orphanSensitive = sensitive.storeComplaintPayload(UUID.randomUUID(), "orphan", "location");
		FileTime old = FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS));
		for (Path path : List.of(
				attachmentDirectory.resolve(keptAttachment.storageKey()),
				attachmentDirectory.resolve(orphanAttachment.storageKey()),
				sensitiveDirectory.resolve(keptSensitive),
				sensitiveDirectory.resolve(orphanSensitive)
		)) {
			Files.setLastModifiedTime(path, old);
		}
		ComplaintAttachmentRepository attachmentRepository = mock(ComplaintAttachmentRepository.class);
		ComplaintSensitivePayloadRepository sensitiveRepository = mock(ComplaintSensitivePayloadRepository.class);
		when(attachmentRepository.findAllStorageKeys()).thenReturn(List.of(keptAttachment.storageKey()));
		when(sensitiveRepository.findAllStorageReferences()).thenReturn(List.of(keptSensitive));
		StorageOrphanCleanupService cleanup = new StorageOrphanCleanupService(
				attachments, sensitive, attachmentRepository, sensitiveRepository, 3600
		);

		StorageOrphanCleanupService.CleanupReport report = cleanup.cleanup();

		assertThat(report.deleted()).isEqualTo(2);
		assertThat(report.failed()).isZero();
		assertThat(Files.exists(attachmentDirectory.resolve(keptAttachment.storageKey()))).isTrue();
		assertThat(Files.exists(attachmentDirectory.resolve(orphanAttachment.storageKey()))).isFalse();
		assertThat(Files.exists(sensitiveDirectory.resolve(keptSensitive))).isTrue();
		assertThat(Files.exists(sensitiveDirectory.resolve(orphanSensitive))).isFalse();
	}
}
