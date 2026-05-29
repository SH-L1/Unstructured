package egovframework.example.complaint.service;

public record StoredFile(
		String storageKey,
		String originalFilename,
		String contentType,
		long size
) {
}
