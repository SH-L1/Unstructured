package egovframework.example.complaint.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileStorageServiceTest {

	@Test
	void reducesUntrustedAttachmentNameToBoundedBasename() {
		String filename = FileStorageService.safeOriginalFilename("../nested\\bad:report\r\n.pdf");

		assertThat(filename).isEqualTo("bad_report__.pdf");
		assertThat(filename).doesNotContain("/", "\\", "\r", "\n");
	}
}
