package egovframework.example.complaint.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocalFileStorageService implements FileStorageService {

	private final Path storageRoot;

	public LocalFileStorageService(@Value("${app.file-storage.local-dir:target/attachments}") String storageRoot) {
		this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
	}

	@Override
	public StoredFile store(String originalFilename, String contentType, long size, InputStream inputStream) {
		String cleanFilename = StringUtils.cleanPath(originalFilename == null ? "attachment" : originalFilename);
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
}
