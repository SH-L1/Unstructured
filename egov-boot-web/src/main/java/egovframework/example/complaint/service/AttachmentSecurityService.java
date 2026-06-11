package egovframework.example.complaint.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AttachmentSecurityService {

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "pdf", "jpg", "jpeg", "png", "hwp", "hwpx");

	private final long maxBytes;

	public AttachmentSecurityService(@Value("${app.attachment.max-bytes:10485760}") long maxBytes) {
		this.maxBytes = maxBytes;
	}

	public Inspection inspect(String filename, String declaredContentType, byte[] bytes) {
		if (bytes.length == 0 || bytes.length > maxBytes) {
			throw new IllegalArgumentException("Attachment must be between 1 byte and " + maxBytes + " bytes");
		}
		String extension = extension(filename);
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("Attachment extension is not allowed: " + extension);
		}
		String detectedType = detectType(bytes);
		if (!isCompatible(extension, detectedType)) {
			throw new IllegalArgumentException("Attachment content does not match its filename extension");
		}
		if (declaredContentType != null && !declaredContentType.isBlank() && !isDeclaredTypeCompatible(declaredContentType, detectedType)) {
			throw new IllegalArgumentException("Attachment content does not match its declared content type");
		}
		return new Inspection(detectedType);
	}

	private String extension(String filename) {
		if (filename == null || !filename.contains(".")) {
			return "";
		}
		return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}

	private String detectType(byte[] bytes) {
		if (startsWith(bytes, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47})) {
			return "image/png";
		}
		if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
			return "image/jpeg";
		}
		if (new String(bytes, 0, Math.min(bytes.length, 5), StandardCharsets.US_ASCII).startsWith("%PDF-")) {
			return "application/pdf";
		}
		if (startsWith(bytes, new byte[] {
				(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
		})) {
			return "application/x-hwp";
		}
		if (startsWith(bytes, new byte[] {0x50, 0x4b, 0x03, 0x04}) && isHwpx(bytes)) {
			return "application/vnd.hancom.hwpx";
		}
		if (startsWith(bytes, new byte[] {(byte) 0xfe, (byte) 0xff}) || startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xfe})) {
			return "text/plain";
		}
		int scanLimit = Math.min(bytes.length, 1024);
		for (int index = 0; index < scanLimit; index++) {
			if (bytes[index] == 0) {
				throw new IllegalArgumentException("Unknown binary attachment type");
			}
		}
		return "text/plain";
	}

	private boolean startsWith(byte[] value, byte[] prefix) {
		if (value.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (value[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private boolean isCompatible(String extension, String detectedType) {
		return switch (extension) {
			case "txt" -> "text/plain".equals(detectedType);
			case "pdf" -> "application/pdf".equals(detectedType);
			case "jpg", "jpeg" -> "image/jpeg".equals(detectedType);
			case "png" -> "image/png".equals(detectedType);
			case "hwp" -> "application/x-hwp".equals(detectedType);
			case "hwpx" -> "application/vnd.hancom.hwpx".equals(detectedType);
			default -> false;
		};
	}

	private boolean isDeclaredTypeCompatible(String declaredType, String detectedType) {
		String normalized = declaredType.toLowerCase(Locale.ROOT);
		if (normalized.equals(detectedType) || normalized.equals("application/octet-stream")) {
			return true;
		}
		return ("application/x-hwp".equals(detectedType)
				&& Set.of("application/haansofthwp", "application/vnd.hancom.hwp").contains(normalized))
				|| ("application/vnd.hancom.hwpx".equals(detectedType)
				&& Set.of("application/zip", "application/x-zip-compressed").contains(normalized));
	}

	private boolean isHwpx(byte[] bytes) {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if ("mimetype".equals(name)
						|| "Contents/content.hpf".equals(name)
						|| "META-INF/manifest.xml".equals(name)) {
					return true;
				}
			}
			return false;
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("Invalid HWPX archive", exception);
		}
	}

	public record Inspection(String detectedType) {
	}
}
