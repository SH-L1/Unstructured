package egovframework.example.complaint.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@ConditionalOnProperty(name = "app.file-storage.provider", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

	private final S3Client s3Client;
	private final String bucketName;
	private final String keyPrefix;

	public S3FileStorageService(
			S3Client s3Client,
			@Value("${app.aws.s3.bucket}") String bucketName,
			@Value("${app.aws.s3.key-prefix:complaints}") String keyPrefix
	) {
		this.s3Client = s3Client;
		this.bucketName = bucketName;
		this.keyPrefix = keyPrefix;
	}

	@Override
	public StoredFile store(String originalFilename, String contentType, long size, InputStream inputStream) {
		if (!StringUtils.hasText(bucketName)) {
			throw new IllegalStateException("S3 bucket is required when app.file-storage.provider=s3");
		}
		String cleanFilename = FileStorageService.safeOriginalFilename(originalFilename);
		String storageKey = normalizePrefix(keyPrefix) + "/" + UUID.randomUUID() + "-" + cleanFilename;
		try {
			PutObjectRequest request = PutObjectRequest.builder()
					.bucket(bucketName)
					.key(storageKey)
					.contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
					.contentLength(size)
					.build();
			s3Client.putObject(request, RequestBody.fromBytes(inputStream.readAllBytes()));
			return new StoredFile(storageKey, cleanFilename, contentType, size);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read attachment for S3 upload", exception);
		}
	}

	@Override
	public StoredFileContent load(String storageKey) {
		requireBucketName();
		GetObjectRequest request = GetObjectRequest.builder()
				.bucket(bucketName)
				.key(storageKey)
				.build();
		ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
		return new StoredFileContent(response.asByteArray());
	}

	@Override
	public void delete(String storageKey) {
		requireBucketName();
		DeleteObjectRequest request = DeleteObjectRequest.builder()
				.bucket(bucketName)
				.key(storageKey)
				.build();
		s3Client.deleteObject(request);
	}

	@Override
	public List<StoredObjectReference> listStoredObjects() {
		requireBucketName();
		ListObjectsV2Request request = ListObjectsV2Request.builder()
				.bucket(bucketName)
				.prefix(normalizePrefix(keyPrefix) + "/")
				.build();
		return s3Client.listObjectsV2Paginator(request).stream()
				.flatMap(page -> page.contents().stream())
				.map(object -> new StoredObjectReference(object.key(), object.lastModified()))
				.toList();
	}

	private String normalizePrefix(String prefix) {
		if (!StringUtils.hasText(prefix)) {
			return "complaints";
		}
		String normalized = prefix.trim();
		if (normalized.startsWith("/") || normalized.startsWith("\\") || normalized.contains("..")
				|| normalized.contains("\\")) {
			throw new IllegalArgumentException("Invalid S3 attachment key prefix");
		}
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	private void requireBucketName() {
		if (!StringUtils.hasText(bucketName)) {
			throw new IllegalStateException("S3 bucket is required when app.file-storage.provider=s3");
		}
	}
}
