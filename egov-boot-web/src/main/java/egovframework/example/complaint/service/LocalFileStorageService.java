package egovframework.example.complaint.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.file-storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

	private final Path storageRoot;

	public LocalFileStorageService(@Value("${app.file-storage.local-dir:target/attachments}") String storageRoot) {
		this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
	}

	@Override
	public StoredFile store(String originalFilename, String contentType, long size, InputStream inputStream) {
		String cleanFilename = FileStorageService.safeOriginalFilename(originalFilename);
		String storageKey = UUID.randomUUID() + "-" + cleanFilename;
		Path target = storageRoot.resolve(storageKey).normalize();
		if (!target.startsWith(storageRoot)) {
			throw new IllegalArgumentException("Invalid attachment filename");
		}
		try {
			Files.createDirectories(storageRoot);
			Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
			return new StoredFile(storageKey, cleanFilename, contentType, size);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to store attachment", exception);
		}
	}

	@Override
	public StoredFileContent load(String storageKey) {
		Path target = resolveStorageKey(storageKey);
		try {
			return new StoredFileContent(Files.readAllBytes(target));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to load attachment", exception);
		}
	}

	@Override
	public void delete(String storageKey) {
		Path target = resolveStorageKey(storageKey);
		try {
			Files.deleteIfExists(target);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to delete attachment", exception);
		}
	}

	@Override
	public List<StoredObjectReference> listStoredObjects() {
		if (!Files.isDirectory(storageRoot)) {
			return List.of();
		}
		try (var paths = Files.list(storageRoot)) {
			return paths.filter(Files::isRegularFile)
					.map(path -> {
						try {
							return new StoredObjectReference(
									path.getFileName().toString(),
									Files.getLastModifiedTime(path).toInstant()
							);
						}
						catch (IOException exception) {
							throw new IllegalStateException("Failed to inspect attachment storage", exception);
						}
					})
					.toList();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to list attachment storage", exception);
		}
	}

	private Path resolveStorageKey(String storageKey) {
		Path target = storageRoot.resolve(storageKey).normalize();
		if (!target.startsWith(storageRoot)) {
			throw new IllegalArgumentException("Invalid attachment storage key");
		}
		return target;
	}
}
