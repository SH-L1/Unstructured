package egovframework.example.complaint.service;

import java.io.InputStream;
import java.util.List;
import org.springframework.util.StringUtils;

public interface FileStorageService {

	StoredFile store(String originalFilename, String contentType, long size, InputStream inputStream);

	StoredFileContent load(String storageKey);

	void delete(String storageKey);

	List<StoredObjectReference> listStoredObjects();

	static String safeOriginalFilename(String originalFilename) {
		String normalized = StringUtils.cleanPath(
				originalFilename == null ? "attachment" : originalFilename.replace('\\', '/')
		);
		String basename = normalized.substring(normalized.lastIndexOf('/') + 1)
				.replaceAll("\\p{Cntrl}", "_")
				.replaceAll("[<>:\"/\\\\|?*]", "_")
				.trim();
		if (!StringUtils.hasText(basename) || ".".equals(basename) || "..".equals(basename)) {
			basename = "attachment";
		}
		return basename.length() <= 240 ? basename : basename.substring(basename.length() - 240);
	}
}
