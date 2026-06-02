package egovframework.example.complaint.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import egovframework.example.EgovBootApplication;
import egovframework.example.complaint.repository.KnowledgeDocumentChunkRepository;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	private KnowledgeDocumentChunkRepository knowledgeDocumentChunkRepository;

	@Test
	void complaintPipelineStoresAnalysisDraftRagAndAttachment() {
		JsonNode created = postComplaint(
				"Illegal dumping with waste bags and old furniture. Bad smell and insects. Please inspect and remove it.",
				"Seoul Jung-gu alley"
		);
		String complaintId = created.path("data").path("id").asText();
		assertThat(created.path("data").path("receiptNumber").asText()).startsWith("CIV-");

		JsonNode analysis = get("/api/complaints/" + complaintId + "/analysis");
		assertThat(analysis.path("success").asBoolean()).isTrue();
		assertThat(analysis.path("data").path("complaintType").asText()).isEqualTo("ILLEGAL_DUMPING");
		assertThat(analysis.path("data").path("departmentCode").asText()).isEqualTo("RESOURCE_RECYCLING");

		JsonNode draft = get("/api/complaints/" + complaintId + "/draft");
		assertThat(draft.path("success").asBoolean()).isTrue();
		assertThat(draft.path("data").path("status").asText()).isEqualTo("DRAFT");
		assertThat(draft.path("data").path("references").size()).isGreaterThanOrEqualTo(1);

		JsonNode revised = put("/api/complaints/" + complaintId + "/draft", Map.of("draftText", "Revised official draft text."));
		assertThat(revised.path("success").asBoolean()).isTrue();
		assertThat(revised.path("data").path("status").asText()).isEqualTo("REVISED");

		JsonNode departments = get("/api/departments");
		assertThat(departments.path("data").size()).isEqualTo(5);
		assertThat(departments.path("data").toString()).contains("SAFETY_CONTROL");
		assertThat(knowledgeDocumentChunkRepository.count()).isGreaterThanOrEqualTo(6);

		JsonNode attachment = uploadAttachment(complaintId);
		assertThat(attachment.path("success").asBoolean()).isTrue();
		assertThat(attachment.path("data").path("originalFilename").asText()).isEqualTo("evidence.txt");

		JsonNode attachments = get("/api/complaints/" + complaintId + "/attachments");
		assertThat(attachments.path("data").size()).isEqualTo(1);
		String attachmentId = attachments.path("data").get(0).path("id").asText();

		ResponseEntity<byte[]> downloaded = restTemplate.getForEntity(
				url("/api/complaints/" + complaintId + "/attachments/" + attachmentId),
				byte[].class
		);
		assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new String(downloaded.getBody(), StandardCharsets.UTF_8)).isEqualTo("mock evidence");

		ResponseEntity<Void> deleted = restTemplate.exchange(
				url("/api/complaints/" + complaintId + "/attachments/" + attachmentId),
				HttpMethod.DELETE,
				HttpEntity.EMPTY,
				Void.class
		);
		assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(get("/api/complaints/" + complaintId + "/attachments").path("data").size()).isZero();
	}

	@Test
	void hazardousMaterialComplaintUsesSafetyDepartmentAndSafetyReferences() {
		JsonNode created = postComplaint(
				"도로에 생화학 위험 물질이 떨어져 있습니다. 아마 폭탄같아요.",
				"테스트 도로"
		);
		String complaintId = created.path("data").path("id").asText();

		JsonNode analysis = get("/api/complaints/" + complaintId + "/analysis");
		assertThat(analysis.path("data").path("complaintType").asText()).isEqualTo("HAZARDOUS_MATERIAL");
		assertThat(analysis.path("data").path("urgency").asText()).isEqualTo("EMERGENCY");
		assertThat(analysis.path("data").path("departmentCode").asText()).isEqualTo("SAFETY_CONTROL");

		JsonNode draft = get("/api/complaints/" + complaintId + "/draft");
		String references = draft.path("data").path("references").toString();
		assertThat(references).contains("생화학");
		assertThat(references).doesNotContain("Waste Management Act");
	}

	private JsonNode postComplaint(String rawText, String locationText) {
		Map<String, String> request = Map.of(
				"sourceChannel", "WEB",
				"rawText", rawText,
				"locationText", locationText
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
		ByteArrayResource resource = new ByteArrayResource("mock evidence".getBytes(StandardCharsets.UTF_8)) {
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
