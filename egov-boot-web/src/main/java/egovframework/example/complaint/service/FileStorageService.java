package egovframework.example.complaint.service;

import java.io.InputStream;

public interface FileStorageService {

	StoredFile store(String originalFilename, String contentType, long size, InputStream inputStream);
}
