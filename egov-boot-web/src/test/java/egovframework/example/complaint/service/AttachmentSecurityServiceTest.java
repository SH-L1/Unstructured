package egovframework.example.complaint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class AttachmentSecurityServiceTest {

	private final AttachmentSecurityService service = new AttachmentSecurityService(1024);

	@Test
	void rejectsContentThatDoesNotMatchExtension() {
		assertThatThrownBy(() -> service.inspect(
				"evidence.pdf",
				"application/pdf",
				"not a pdf".getBytes(StandardCharsets.UTF_8)
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match");
	}

	@Test
	void rejectsUnknownBinaryContent() {
		assertThatThrownBy(() -> service.inspect(
				"evidence.txt",
				"text/plain",
				new byte[] {1, 2, 0, 4}
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unknown binary");
	}

	@Test
	void acceptsHwpAndHwpxOnlyWhenMagicBytesMatch() throws IOException {
		assertThat(service.inspect(
				"evidence.hwp",
				"application/haansofthwp",
				new byte[] {
						(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
				}
		).detectedType()).isEqualTo("application/x-hwp");

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(output)) {
			zip.putNextEntry(new ZipEntry("Contents/content.hpf"));
			zip.write("<package/>".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		assertThat(service.inspect(
				"evidence.hwpx",
				"application/zip",
				output.toByteArray()
		).detectedType()).isEqualTo("application/vnd.hancom.hwpx");
	}
}
