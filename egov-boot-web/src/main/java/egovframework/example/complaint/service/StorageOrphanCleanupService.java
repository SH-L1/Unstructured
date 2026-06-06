package egovframework.example.complaint.service;

import egovframework.example.complaint.repository.ComplaintAttachmentRepository;
import egovframework.example.complaint.repository.ComplaintSensitivePayloadRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
		name = "app.storage-orphan-cleanup.enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class StorageOrphanCleanupService {

	private static final Logger log = LoggerFactory.getLogger(StorageOrphanCleanupService.class);

	private final FileStorageService fileStorageService;
	private final SensitivePayloadStorageService sensitivePayloadStorageService;
	private final ComplaintAttachmentRepository complaintAttachmentRepository;
	private final ComplaintSensitivePayloadRepository complaintSensitivePayloadRepository;
	private final long graceSeconds;

	public StorageOrphanCleanupService(
			FileStorageService fileStorageService,
			SensitivePayloadStorageService sensitivePayloadStorageService,
			ComplaintAttachmentRepository complaintAttachmentRepository,
			ComplaintSensitivePayloadRepository complaintSensitivePayloadRepository,
			@Value("${app.storage-orphan-cleanup.grace-seconds:86400}") long graceSeconds
	) {
		this.fileStorageService = fileStorageService;
		this.sensitivePayloadStorageService = sensitivePayloadStorageService;
		this.complaintAttachmentRepository = complaintAttachmentRepository;
		this.complaintSensitivePayloadRepository = complaintSensitivePayloadRepository;
		this.graceSeconds = Math.max(3600, graceSeconds);
	}

	@Scheduled(
			initialDelayString = "${app.storage-orphan-cleanup.initial-delay-ms:3600000}",
			fixedDelayString = "${app.storage-orphan-cleanup.fixed-delay-ms:21600000}"
	)
	public void cleanupScheduled() {
		CleanupReport report = cleanup();
		if (report.deleted() > 0 || report.failed() > 0) {
			log.info("Storage orphan cleanup scanned={}, deleted={}, failed={}",
					report.scanned(), report.deleted(), report.failed());
		}
	}

	public CleanupReport cleanup() {
		Instant cutoff = Instant.now().minusSeconds(graceSeconds);
		Set<String> attachmentReferences = new HashSet<>(complaintAttachmentRepository.findAllStorageKeys());
		Set<String> sensitiveReferences = new HashSet<>(complaintSensitivePayloadRepository.findAllStorageReferences());
		CleanupReport attachments = cleanup(
				fileStorageService.listStoredObjects(),
				attachmentReferences,
				cutoff,
				fileStorageService::delete
		);
		CleanupReport sensitive = cleanup(
				sensitivePayloadStorageService.listStoredObjects(),
				sensitiveReferences,
				cutoff,
				sensitivePayloadStorageService::delete
		);
		return attachments.plus(sensitive);
	}

	private CleanupReport cleanup(
			List<StoredObjectReference> objects,
			Set<String> referenced,
			Instant cutoff,
			Consumer<String> delete
	) {
		int deleted = 0;
		int failed = 0;
		for (StoredObjectReference object : objects) {
			if (referenced.contains(object.storageKey()) || !object.lastModifiedAt().isBefore(cutoff)) {
				continue;
			}
			try {
				delete.accept(object.storageKey());
				deleted++;
			}
			catch (RuntimeException exception) {
				failed++;
				log.warn("Could not delete unreferenced storage object {}", object.storageKey(), exception);
			}
		}
		return new CleanupReport(objects.size(), deleted, failed);
	}

	public record CleanupReport(int scanned, int deleted, int failed) {
		private CleanupReport plus(CleanupReport other) {
			return new CleanupReport(scanned + other.scanned, deleted + other.deleted, failed + other.failed);
		}
	}
}
