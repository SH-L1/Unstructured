package egovframework.example.complaint.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import egovframework.example.EgovBootApplication;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(classes = EgovBootApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ComplaintApiSmokeTest {

	@LocalServerPort
	private int port;

	private final TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void complaintPipelineStoresAnalysisDraftRagAndAttachment() {
		JsonNode created = postComplaint();
		String complaintId = created.path("data").path("id").asText();

		JsonNode analysis = get("/api/complaints/" + complaintId + "/analysis");
		assertThat(analysis.path("success").asBoolean()).isTrue();
		assertThat(analysis.path("data").path("departmentCode").asText()).isEqualTo("RESOURCE_RECYCLING");
		assertThat(analysis.path("data").path("geoJson").asText()).contains("Seoul Jung-gu alley");

		JsonNode draft = get("/api/complaints/" + complaintId + "/draft");
		assertThat(draft.path("success").asBoolean()).isTrue();
		assertThat(draft.path("data").path("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.path("data").path("references").size()).isEqualTo(3);

		JsonNode revised = put("/api/complaints/" + complaintId + "/draft", Map.of("draftText", "Revised official draft text."));
		assertThat(revised.path("success").asBoolean()).isTrue();
		assertThat(revised.path("data").path("status").asText()).isEqualTo("REVISED");

		JsonNode rag = get("/api/complaints/" + complaintId + "/rag-contexts");
		assertThat(rag.path("data").size()).isEqualTo(3);

		JsonNode departments = get("/api/departments");
		assertThat(departments.path("data").size()).isEqualTo(4);

		JsonNode attachment = uploadAttachment(complaintId);
		assertThat(attachment.path("success").asBoolean()).isTrue();
		assertThat(attachment.path("data").path("originalFilename").asText()).isEqualTo("evidence.txt");

		JsonNode attachments = get("/api/complaints/" + complaintId + "/attachments");
		assertThat(attachments.path("data").size()).isEqualTo(1);
		String attachmentId = attachments.path("data").get(0).path("id").asText();

		ResponseEntity<String> downloaded = restTemplate.getForEntity(
				url("/api/complaints/" + complaintId + "/attachments/" + attachmentId),
				String.class
		);
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(downloaded.getBody()).isEqualTo("mock evidence");

		ResponseEntity<Void> deleted = restTemplate.exchange(
				url("/api/complaints/" + complaintId + "/attachments/" + attachmentId),
				HttpMethod.DELETE,
				HttpEntity.EMPTY,
				Void.class
		);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		JsonNode afterDelete = get("/api/complaints/" + complaintId + "/attachments");
		assertThat(afterDelete.path("data").size()).isZero();
	}

	private JsonNode postComplaint() {
		Map<String, String> request = Map.of(
				"sourceChannel", "WEB",
				"rawText", "Illegal dumping with waste bags and old furniture. Bad smell and insects. Please inspect and remove it.",
				"locationText", "Seoul Jung-gu alley"
		);
		ResponseEntity<JsonNode> response = restTemplate.postForEntity(url("/api/complaints"), request, JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private JsonNode get(String path) {
		ResponseEntity<JsonNode> response = restTemplate.getForEntity(url(path), JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private JsonNode put(String path, Map<String, String> request) {
		ResponseEntity<JsonNode> response = restTemplate.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(request), JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private JsonNode uploadAttachment(String complaintId) {
		ByteArrayResource resource = new ByteArrayResource("mock evidence".getBytes()) {
			@Override
			public String getFilename() {
				return "evidence.txt";
			}
		};
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", resource);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		ResponseEntity<JsonNode> response = restTemplate.postForEntity(
				url("/api/complaints/" + complaintId + "/attachments"),
				new HttpEntity<>(body, headers),
				JsonNode.class
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}
}
