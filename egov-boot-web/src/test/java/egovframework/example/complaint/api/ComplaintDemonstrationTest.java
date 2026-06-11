package egovframework.example.complaint.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import egovframework.example.EgovBootApplication;
import egovframework.example.complaint.domain.DocumentType;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.KnowledgePurpose;
import egovframework.example.complaint.domain.KnowledgeVerificationStatus;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = EgovBootApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ComplaintDemonstrationTest {

	@LocalServerPort
	private int port;

	private final TestRestTemplate restTemplate = new TestRestTemplate();

	@Autowired
	private KnowledgeDocumentRepository knowledgeDocumentRepository;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUpOfficialEvidence() {
		// Clean tables to avoid constraint violations or test contamination
		jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
		jdbcTemplate.execute("truncate table claim_evidence_links");
		jdbcTemplate.execute("truncate table draft_claims");
		jdbcTemplate.execute("truncate table rag_contexts");
		jdbcTemplate.execute("truncate table official_drafts");
		jdbcTemplate.execute("truncate table department_tasks");
		jdbcTemplate.execute("truncate table location_candidates");
		jdbcTemplate.execute("truncate table complaint_issues");
		jdbcTemplate.execute("truncate table verification_results");
		jdbcTemplate.execute("truncate table ai_runs");
		jdbcTemplate.execute("truncate table processing_jobs");
		jdbcTemplate.execute("truncate table complaints");
		jdbcTemplate.execute("truncate table audit_logs");
		jdbcTemplate.execute("truncate table workflow_audit_events");
		jdbcTemplate.execute("truncate table complaint_sensitive_payloads");
		jdbcTemplate.execute("delete from knowledge_document_chunks where knowledge_document_id not in (select id from knowledge_documents where title in ('Legacy waste handling summary', 'Legacy road damage response summary', 'Legacy illegal parking response summary'))");
		jdbcTemplate.execute("delete from knowledge_purpose where knowledge_document_id not in (select id from knowledge_documents where title in ('Legacy waste handling summary', 'Legacy road damage response summary', 'Legacy illegal parking response summary'))");
		jdbcTemplate.execute("delete from knowledge_documents where title not in ('Legacy waste handling summary', 'Legacy road damage response summary', 'Legacy illegal parking response summary')");
		jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

		// Verify official evidence so that draft generation works
		knowledgeDocumentRepository.findAll().forEach(document -> {
			if (document.getTitle().contains("waste") || document.getTitle().contains("parking")) {
				document.verifyForTest(
						KnowledgePurpose.OFFICIAL_LAW,
						KnowledgeVerificationStatus.VERIFIED_OFFICIAL,
						"NATIONAL",
						LocalDate.now().minusYears(1),
						null
				);
			} else {
				document.verifyForTest(
						KnowledgePurpose.PROCEDURE,
						KnowledgeVerificationStatus.VERIFIED_INTERNAL,
						"ASAN",
						LocalDate.now().minusYears(1),
						null
				);
			}
			knowledgeDocumentRepository.save(document);
		});

		if (knowledgeDocumentRepository.findByTitle("General civil complaint handling manual").isEmpty()) {
			KnowledgeDocument generalDoc = new KnowledgeDocument(
					DocumentType.MANUAL,
					"General civil complaint handling manual",
					"General guidance",
					"https://example.invalid/general-manual",
					"General civil complaints are handled by Civil Affairs department.",
					"일반 민원,일반,민원,general",
					"General handling reference"
			);
			generalDoc.verifyForTest(
					KnowledgePurpose.PROCEDURE,
					KnowledgeVerificationStatus.VERIFIED_INTERNAL,
					"ASAN",
					LocalDate.now().minusYears(1),
					null
			);
			knowledgeDocumentRepository.save(generalDoc);
		}
	}

	@Test
	public void testCaseA_RegulatoryDumpingWithPII() {
		// 1. Create complaint with PII and location
		String rawText = "쓰레기 무단투기 신고합니다. 주민등록번호 950815-1234567을 사용하는 사람입니다.";
		String locationText = "아산시 온천동 123-4";
		JsonNode created = createComplaint(rawText, locationText, key());
		String complaintId = created.path("data").path("id").asText();

		// Verify PII is redacted
		assertThat(created.path("data").path("redactedText").asText()).contains("[REDACTED_ID]");
		assertThat(created.path("data").path("redactedText").asText()).doesNotContain("950815-1234567");

		// 2. Start analysis run
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		// 3. Confirm department selection
		JsonNode detail = confirmFirstDepartment(complaintId);

		// 4. Generate draft
		JsonNode draftJob = startRun(complaintId, "draft-runs", detail.path("complaint").path("version").asLong(), key());
		assertThat(waitForJob(draftJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		// 5. Review draft
		JsonNode draftDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		long draftId = draftDetail.path("draft").path("id").asLong();
		JsonNode reviewed = decide("/api/v1/drafts/" + draftId + "/reviews", 0, true, key());
		assertThat(reviewed.path("data").path("status").asText()).isEqualTo("APPROVAL_PENDING");

		// 6. Approve draft
		JsonNode approved = decide("/api/v1/drafts/" + draftId + "/approvals", reviewed.path("data").path("version").asLong(), true, key());
		assertThat(approved.path("data").path("status").asText()).isEqualTo("APPROVED");

		// 7. Complete complaint
		JsonNode approvedDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		JsonNode completed = postWithoutBody("/api/v1/complaints/" + complaintId + "/complete", approvedDetail.path("complaint").path("version").asLong(), key());
		assertThat(completed.path("data").path("status").asText()).isEqualTo("COMPLETED");
	}

	@Test
	public void testCaseB_RoadDamageWithMissingLocation() {
		// 1. Create complaint without location
		String rawText = "도로에 포트홀이 심해서 타이어가 터질 뻔 했습니다.";
		JsonNode created = createComplaint(rawText, null, key());
		String complaintId = created.path("data").path("id").asText();

		// 2. Start analysis run
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		// 3. Verify blocked by NEEDS_LOCATION
		JsonNode detail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(detail.path("complaint").path("workflowBlocker").asText()).isEqualTo("NEEDS_LOCATION");

		// Try draft run and verify it is blocked (returns 409 CONFLICT)
		ResponseEntity<JsonNode> draftAttempt = post("/api/v1/complaints/" + complaintId + "/draft-runs", null, detail.path("complaint").path("version").asLong(), key());
		assertThat(draftAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		// 4. Confirm location by human
		String issueId = detail.path("issues").get(0).path("id").asText();
		ResponseEntity<JsonNode> confirmedLoc = post(
				"/api/v1/issues/" + issueId + "/location-confirmations",
				Map.of("locationText", "아산로 45번길"),
				detail.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(confirmedLoc.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(confirmedLoc.getBody().path("data").path("workflowBlocker").isNull()).isTrue();

		// 5. Confirm department
		JsonNode afterLocDetail = confirmedLoc.getBody().path("data");
		JsonNode afterDepDetail = confirmFirstDepartment(complaintId);

		// 6. Generate draft
		JsonNode draftJob = startRun(complaintId, "draft-runs", afterDepDetail.path("complaint").path("version").asLong(), key());
		assertThat(waitForJob(draftJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");
	}

	@Test
	public void testCaseC_GeneralComplaintNeedsJurisdiction() {
		// 1. Create general complaint "돈 주세요" with location
		String rawText = "돈 주세요. 생활비가 필요해요.";
		String locationText = "아산시청";
		JsonNode created = createComplaint(rawText, locationText, key());
		String complaintId = created.path("data").path("id").asText();

		// 2. Start analysis run
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		// 3. Verify blocked by NEEDS_JURISDICTION
		JsonNode detail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(detail.path("complaint").path("workflowBlocker").asText()).isEqualTo("NEEDS_JURISDICTION");

		// 4. Confirm department (VERIFIED) -> Should resolve the blocker!
		String issueId = detail.path("issues").get(0).path("id").asText();
		ResponseEntity<JsonNode> confirmedDep = post(
				"/api/v1/issues/" + issueId + "/department-confirmations",
				Map.of("departmentCode", "CIVIL_AFFAIRS"),
				detail.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(confirmedDep.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(confirmedDep.getBody().path("data").path("complaint").path("workflowBlocker").isNull()).isTrue(); // Blocker should be cleared!

		// 5. Generate draft
		JsonNode afterDepDetail = confirmedDep.getBody().path("data");
		JsonNode draftJob = startRun(complaintId, "draft-runs", afterDepDetail.path("complaint").path("version").asLong(), key());
		assertThat(waitForJob(draftJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");
	}

	private JsonNode createComplaint(String rawText, String locationText, String idempotencyKey) {
		Map<String, String> request = locationText == null
				? Map.of("sourceChannel", "WEB", "rawText", rawText)
				: Map.of("sourceChannel", "WEB", "rawText", rawText, "locationText", locationText);
		ResponseEntity<JsonNode> response = post("/api/v1/complaints", request, null, idempotencyKey);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody();
	}

	private JsonNode startRun(String complaintId, String runType, long version, String idempotencyKey) {
		ResponseEntity<JsonNode> response = post(
				"/api/v1/complaints/" + complaintId + "/" + runType,
				null,
				version,
				idempotencyKey
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		return response.getBody();
	}

	private JsonNode confirmFirstDepartment(String complaintId) {
		JsonNode detail = getOk("/api/v1/complaints/" + complaintId).path("data");
		JsonNode issue = detail.path("issues").get(0);
		String issueId = issue.path("id").asText();
		String departmentCode = issue.path("departmentCandidates").get(0).asText();
		ResponseEntity<JsonNode> response = post(
				"/api/v1/issues/" + issueId + "/department-confirmations",
				Map.of("departmentCode", departmentCode),
				detail.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode confirmed = response.getBody().path("data");
		assertThat(confirmed.path("issues").get(0).path("departmentCandidateDetails").get(0).path("verified").asBoolean())
				.isTrue();
		return confirmed;
	}

	private JsonNode decide(String path, long version, boolean approved, String idempotencyKey) {
		ResponseEntity<JsonNode> response = post(path, Map.of("approved", approved, "notes", "reviewed"), version, idempotencyKey);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private JsonNode postWithoutBody(String path, long version, String idempotencyKey) {
		ResponseEntity<JsonNode> response = post(path, null, version, idempotencyKey);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private ResponseEntity<JsonNode> post(String path, Object body, Long version, String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Idempotency-Key", idempotencyKey);
		if (version != null) {
			headers.setIfMatch("\"" + version + "\"");
		}
		return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
	}

	private JsonNode getOk(String path) {
		ResponseEntity<JsonNode> response = restTemplate.getForEntity(url(path), JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private JsonNode waitForJob(String jobId) {
		for (int attempt = 0; attempt < 100; attempt++) {
			JsonNode job = getOk("/api/v1/runs/" + jobId).path("data");
			if (job.path("status").asText().matches("SUCCEEDED|FAILED|BLOCKED")) {
				return job;
			}
			try {
				Thread.sleep(50);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for processing job", exception);
			}
		}
		throw new IllegalStateException("Processing job did not reach a terminal state");
	}

	private String key() {
		return UUID.randomUUID().toString();
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}
}
