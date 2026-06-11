package egovframework.example.complaint.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.example.EgovBootApplication;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ProcessingJob;
import egovframework.example.complaint.domain.ProcessingJobType;
import egovframework.example.complaint.domain.SourceChannel;
import egovframework.example.complaint.repository.ComplaintAnalysisRepository;
import egovframework.example.complaint.repository.ComplaintIssueRepository;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.ProcessingJobRepository;
import egovframework.example.complaint.service.ContentHashService;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
		classes = EgovBootApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"spring.datasource.url=jdbc:h2:mem:worker-contract-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
				"app.jobs.execution-mode=python",
				"app.worker.service-token=test-worker-service-token-at-least-32-characters",
				"app.security.session.enabled=false"
		}
)
@ActiveProfiles("test")
class WorkerJobApiTest {

	private static final String TOKEN = "test-worker-service-token-at-least-32-characters";

	@LocalServerPort
	private int port;

	private final TestRestTemplate restTemplate = new TestRestTemplate();

	@Autowired
	private ComplaintRepository complaintRepository;

	@Autowired
	private ProcessingJobRepository processingJobRepository;

	@Autowired
	private ComplaintAnalysisRepository complaintAnalysisRepository;

	@Autowired
	private ComplaintIssueRepository complaintIssueRepository;

	@Autowired
	private ContentHashService hashService;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearWorkflowRows() {
		for (String table : List.of(
				"claim_evidence_links", "draft_claims", "rag_contexts", "evidence_snapshots", "retrieval_runs",
				"verification_results", "human_reviews", "department_tasks", "location_candidates",
				"complaint_issues", "ai_runs", "complaint_analysis", "processing_jobs", "official_drafts",
				"complaint_sensitive_payloads", "complaint_attachments", "complaints"
		)) {
			jdbcTemplate.update("delete from " + table);
		}
	}

	@Test
	void rejectsMissingInternalServiceToken() {
		ResponseEntity<JsonNode> response = post(
				"/internal/v1/worker/jobs/claim",
				Map.of("workerId", "worker-test", "jobTypes", List.of("CLASSIFY_ISSUES")),
				null
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void claimsOnlyRedactedInputAndAppliesValidatedAnalysisResult() throws Exception {
		ProcessingJob job = analysisJob();
		ResponseEntity<JsonNode> claimResponse = post(
				"/internal/v1/worker/jobs/claim",
				Map.of("workerId", "worker-test", "jobTypes", List.of("CLASSIFY_ISSUES")),
				TOKEN
		);

		assertThat(claimResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode claim = claimResponse.getBody().path("data");
		assertThat(claim.path("id").asText()).isEqualTo(job.getId().toString());
		assertThat(claim.toString()).doesNotContain("RAW SECRET", "010-1234-5678");
		assertThat(claim.path("payload").path("redactedText").asText()).contains("[REDACTED_PHONE]");

		Map<String, Object> output = analysisOutput();
		ResponseEntity<JsonNode> resultResponse = post(
				"/internal/v1/worker/jobs/" + job.getId() + "/results",
				resultRequest(claim.path("inputHash").asText(), output),
				TOKEN
		);

		assertThat(resultResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(resultResponse.getBody().path("data").path("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(complaintAnalysisRepository.findByComplaintId(job.getComplaintId())).isPresent();
		assertThat(complaintIssueRepository.findByComplaint_IdOrderByIssueIndexAsc(job.getComplaintId())).hasSize(1);
	}

	@Test
	void rejectsTamperedGovernedInputHash() throws Exception {
		ProcessingJob job = analysisJob();
		JsonNode claim = post(
				"/internal/v1/worker/jobs/claim",
				Map.of("workerId", "worker-test", "jobTypes", List.of("CLASSIFY_ISSUES")),
				TOKEN
		).getBody().path("data");
		Map<String, Object> request = resultRequest("a".repeat(64), analysisOutput());

		ResponseEntity<JsonNode> response = post(
				"/internal/v1/worker/jobs/" + job.getId() + "/results",
				request,
				TOKEN
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(processingJobRepository.findById(job.getId()).orElseThrow().getStatus().name()).isEqualTo("RUNNING");
		assertThat(claim.path("inputHash").asText()).isNotEqualTo("a".repeat(64));
	}

	@Test
	void recordsInvalidAiResultAsAuditableBlockedJob() throws Exception {
		ProcessingJob job = analysisJob();
		JsonNode claim = post(
				"/internal/v1/worker/jobs/claim",
				Map.of("workerId", "worker-test", "jobTypes", List.of("CLASSIFY_ISSUES")),
				TOKEN
		).getBody().path("data");
		Map<String, Object> invalidOutput = analysisOutput();
		invalidOutput.put("schemaVersion", "unsupported-schema");

		ResponseEntity<JsonNode> response = post(
				"/internal/v1/worker/jobs/" + job.getId() + "/results",
				resultRequest(claim.path("inputHash").asText(), invalidOutput),
				TOKEN
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().path("data").path("status").asText()).isEqualTo("BLOCKED");
		assertThat(complaintRepository.findById(job.getComplaintId()).orElseThrow().getWorkflowBlocker().name())
				.isEqualTo("PROCESSING_FAILED");
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from ai_runs where processing_job_id = ?",
				Integer.class,
				job.getId()
		)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from workflow_audit_events where entity_id = ? and action = 'BLOCK_CLASSIFY_ISSUES'",
				Integer.class,
				job.getId().toString()
		)).isEqualTo(1);
	}

	@Test
	void supportJobLeaseAndCompletionRemainSpringAuthoritative() {
		Complaint complaint = complaintRepository.saveAndFlush(new Complaint(
				SourceChannel.WEB,
				"RAW SECRET 010-1234-5678",
				"Road damage reported by [REDACTED_PHONE]",
				"Synthetic pilot road"
		));
		ProcessingJob job = processingJobRepository.saveAndFlush(new ProcessingJob(
				complaint,
				ProcessingJobType.REDACT,
				"support-worker-test-" + UUID.randomUUID(),
				3
		));
		JsonNode claim = post(
				"/internal/v1/worker/jobs/claim",
				Map.of("workerId", "worker-test", "jobTypes", List.of("REDACT")),
				TOKEN
		).getBody().path("data");

		assertThat(claim.path("id").asText()).isEqualTo(job.getId().toString());
		assertThat(claim.toString()).doesNotContain("RAW SECRET", "010-1234-5678");
		ResponseEntity<JsonNode> response = post(
				"/internal/v1/worker/jobs/" + job.getId() + "/support-results",
				Map.of(
						"workerId", "worker-test",
						"inputHash", claim.path("inputHash").asText(),
						"resultReference", complaint.getId().toString()
				),
				TOKEN
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().path("data").path("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(processingJobRepository.findById(job.getId()).orElseThrow().getStatus().name()).isEqualTo("SUCCEEDED");
	}

	private ProcessingJob analysisJob() {
		Complaint complaint = complaintRepository.saveAndFlush(new Complaint(
				SourceChannel.WEB,
				"RAW SECRET 010-1234-5678",
				"Road damage reported by [REDACTED_PHONE]",
				"Synthetic pilot road"
		));
		return processingJobRepository.saveAndFlush(new ProcessingJob(
				complaint,
				ProcessingJobType.CLASSIFY_ISSUES,
				"worker-test-" + UUID.randomUUID(),
				3
		));
	}

	private Map<String, Object> analysisOutput() {
		Map<String, Object> issue = new LinkedHashMap<>();
		issue.put("summary", "Road damage");
		issue.put("complaintType", "ROAD_DAMAGE");
		issue.put("jurisdictionStatus", "ASAN_CANDIDATE");
		issue.put("safetyRisk", "HIGH");
		issue.put("expressionRisk", "NORMAL");
		issue.put("processability", "PROCESSABLE");
		issue.put("departmentCandidates", List.of("ROAD"));
		issue.put("locationCandidates", List.of("Synthetic pilot road"));
		issue.put("evidenceIds", List.of());
		Map<String, Object> output = new LinkedHashMap<>();
		output.put("schemaVersion", "complaint-support-v1");
		output.put("intent", "Road damage");
		output.put("urgency", "HIGH");
		output.put("sentiment", "NEUTRAL");
		output.put("departmentCode", "ROAD");
		output.put("locationText", "Synthetic pilot road");
		output.put("keywords", List.of("road", "damage"));
		output.put("requiredAction", "Staff review");
		output.put("issues", List.of(issue));
		return output;
	}

	private Map<String, Object> resultRequest(String inputHash, Map<String, Object> output) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("workerId", "worker-test");
		request.put("provider", "openai");
		request.put("modelName", "gpt-4o-mini");
		request.put("promptVersion", "issue-analysis-prompt-v1");
		request.put("schemaVersion", "complaint-support-v1");
		request.put("inputHash", inputHash);
		request.put("outputHash", hashService.sha256(objectMapper.writeValueAsString(output)));
		request.put("costUnits", 0);
		request.put("durationMs", 10);
		request.put("retryCount", 0);
		request.put("output", output);
		request.put("evidenceDocumentIds", List.of());
		return request;
	}

	private ResponseEntity<JsonNode> post(String path, Object body, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (token != null) {
			headers.setBearerAuth(token);
		}
		return restTemplate.postForEntity("http://localhost:" + port + path, new HttpEntity<>(body, headers), JsonNode.class);
	}
}
