package egovframework.example.complaint.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SensitivePayloadStorageService {

	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH_BITS = 128;

	private final Path storageDirectory;
	private final SecretKeySpec encryptionKey;
	private final SecureRandom secureRandom = new SecureRandom();
	private final ObjectMapper objectMapper;

	public SensitivePayloadStorageService(
			@Value("${app.sensitive-storage.local-dir:target/sensitive-payloads}") String localDirectory,
			@Value("${app.sensitive-storage.key}") String key,
			ObjectMapper objectMapper
	) {
		if (key == null || key.length() < 32) {
			throw new IllegalStateException("Sensitive payload encryption key must contain at least 32 characters");
		}
		this.storageDirectory = Path.of(localDirectory).toAbsolutePath().normalize();
		this.encryptionKey = new SecretKeySpec(sha256(key), "AES");
		this.objectMapper = objectMapper;
	}

	public String storeComplaintPayload(UUID complaintId, String rawText, String locationText) {
		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("rawText", rawText);
		payload.put("locationText", locationText);
		try {
			return store(complaintId, objectMapper.writeValueAsString(payload));
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize sensitive complaint payload", exception);
		}
	}

	private String store(UUID complaintId, String rawText) {
		try {
			Files.createDirectories(storageDirectory);
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(rawText.getBytes(StandardCharsets.UTF_8));
			byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
			String filename = complaintId + "-" + UUID.randomUUID() + ".enc";
			Path target = storageDirectory.resolve(filename).normalize();
			if (!target.startsWith(storageDirectory)) {
				throw new IllegalStateException("Sensitive payload path escaped storage directory");
			}
			Files.write(target, payload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			return filename;
		}
		catch (IOException | GeneralSecurityException exception) {
			throw new IllegalStateException("Failed to store encrypted sensitive complaint payload", exception);
		}
	}

	public void delete(String storageReference) {
		Path target = storageDirectory.resolve(storageReference).normalize();
		if (!target.startsWith(storageDirectory)) {
			throw new IllegalArgumentException("Sensitive payload path escaped storage directory");
		}
		try {
			Files.deleteIfExists(target);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to delete encrypted sensitive complaint payload", exception);
		}
	}

	public List<StoredObjectReference> listStoredObjects() {
		if (!Files.isDirectory(storageDirectory)) {
			return List.of();
		}
		try (var paths = Files.list(storageDirectory)) {
			return paths.filter(Files::isRegularFile)
					.map(path -> {
						try {
							return new StoredObjectReference(
									path.getFileName().toString(),
									Files.getLastModifiedTime(path).toInstant()
							);
						}
						catch (IOException exception) {
							throw new IllegalStateException("Failed to inspect sensitive payload storage", exception);
						}
					})
					.toList();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to list sensitive payload storage", exception);
		}
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
