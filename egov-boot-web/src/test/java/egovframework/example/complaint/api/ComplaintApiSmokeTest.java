package egovframework.example.complaint.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import egovframework.example.EgovBootApplication;
import egovframework.example.complaint.domain.KnowledgePurpose;
import egovframework.example.complaint.domain.KnowledgeVerificationStatus;
import egovframework.example.complaint.domain.KnowledgeDocument;
import egovframework.example.complaint.domain.DocumentType;
import egovframework.example.complaint.repository.ComplaintRepository;
import egovframework.example.complaint.repository.ComplaintSensitivePayloadRepository;
import egovframework.example.complaint.repository.DraftClaimRepository;
import egovframework.example.complaint.repository.KnowledgeDocumentRepository;
import egovframework.example.complaint.repository.AuditLogRepository;
import egovframework.example.complaint.repository.ProcessingJobRepository;
import egovframework.example.complaint.repository.WorkflowAuditEventRepository;
import egovframework.example.complaint.service.TrustWorkflowService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(classes = EgovBootApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ComplaintApiSmokeTest {

	@LocalServerPort
	private int port;

	private final TestRestTemplate restTemplate = new TestRestTemplate();

	@Autowired
	private KnowledgeDocumentRepository knowledgeDocumentRepository;

	@Autowired
	private ComplaintRepository complaintRepository;

	@Autowired
	private ComplaintSensitivePayloadRepository complaintSensitivePayloadRepository;

	@Autowired
	private WorkflowAuditEventRepository workflowAuditEventRepository;

	@Autowired
	private DraftClaimRepository draftClaimRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private ProcessingJobRepository processingJobRepository;

	@Autowired
	private TrustWorkflowService workflowService;

	@BeforeEach
	void resetKnowledgeVerification() {
		knowledgeDocumentRepository.findAll().forEach(document -> {
			document.verifyForTest(
					KnowledgePurpose.UNVERIFIED_LEGACY,
					KnowledgeVerificationStatus.UNVERIFIED_LEGACY,
					null,
					null,
					null
			);
			knowledgeDocumentRepository.save(document);
		});
	}

	@Test
	void blocksDraftWithoutVerifiedOfficialEvidenceAndRedactsExternalView() {
		JsonNode created = createComplaint(
				"Illegal dumping reported by 010-1234-5678 and citizen@example.com.",
				"Pilot alley near 010-9876-5432",
				key()
		);
		String complaintId = created.path("data").path("id").asText();
		var storedComplaint = complaintRepository.findById(UUID.fromString(complaintId)).orElseThrow();
		assertThat(created.path("data").path("redactedText").asText()).contains("[REDACTED_PHONE]", "[REDACTED_EMAIL]");
		assertThat(created.path("data").path("redactedText").asText()).doesNotContain("010-1234-5678", "citizen@example.com");
		assertThat(created.path("data").path("locationText").asText()).contains("[REDACTED_PHONE]");
		assertThat(created.path("data").path("locationText").asText()).doesNotContain("010-9876-5432");
		assertThat(storedComplaint.getRawText()).doesNotContain("010-1234-5678", "citizen@example.com");
		assertThat(storedComplaint.getLocationText()).doesNotContain("010-9876-5432");
		assertThat(complaintSensitivePayloadRepository.count()).isGreaterThanOrEqualTo(1);

		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode detail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(detail.path("complaint").path("status").asText()).isEqualTo("TRIAGE_REVIEW");
		assertThat(detail.path("issues").size()).isEqualTo(1);
		assertThat(detail.path("analysis").path("analysisJson").asText())
				.contains("\"schemaVersion\":\"complaint-support-v1\"");

		ResponseEntity<JsonNode> unconfirmedDraft = post(
				"/api/v1/complaints/" + complaintId + "/draft-runs",
				null,
				detail.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(unconfirmedDraft.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(unconfirmedDraft.getBody().path("error").path("message").asText())
				.contains("human-selected and verified department");

		detail = confirmFirstDepartment(complaintId);
		JsonNode draftJob = startRun(
				complaintId,
				"draft-runs",
				detail.path("complaint").path("version").asLong(),
				key()
		);
		JsonNode terminalJob = waitForJob(draftJob.path("data").path("id").asText());
		assertThat(terminalJob.path("status").asText()).isEqualTo("BLOCKED");
		assertThat(terminalJob.path("failureReason").asText()).contains("Verified official evidence");
		assertThat(getOk("/api/v1/complaints/" + complaintId)
				.path("data").path("complaint").path("workflowBlocker").asText()).isEqualTo("EVIDENCE_INSUFFICIENT");

		verifyOfficialWasteEvidence();
		JsonNode blockedDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		JsonNode retryDraftJob = startRun(
				complaintId,
				"draft-runs",
				blockedDetail.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(waitForJob(retryDraftJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");
		JsonNode recoveredDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(recoveredDetail.path("complaint").path("status").asText()).isEqualTo("DRAFT_REVIEW");
		assertThat(recoveredDetail.path("complaint").path("workflowBlocker").isNull()).isTrue();
	}

	@Test
	void attachmentUploadCreatesQuarantinedExtractionJob() {
		JsonNode created = createComplaint("Road damage with an attached note.", "Pilot road", key());
		UUID complaintId = UUID.fromString(created.path("data").path("id").asText());
		long version = created.path("data").path("version").asLong();
		var attachment = workflowService.addAttachment(
				complaintId,
				new MockMultipartFile(
						"file",
						"inspection.txt",
						"text/plain",
						"Field note without personal data.".getBytes(StandardCharsets.UTF_8)
				),
				key(),
				version
		);

		assertThat(processingJobRepository.findAll())
				.filteredOn(job -> "EXTRACT_ATTACHMENT".equals(job.getJobType().name())
						&& attachment.id().toString().equals(job.getPayloadReference()))
				.singleElement()
				.satisfies(job -> assertThat(job.getStatus().name()).isEqualTo("PENDING"));
		assertThatThrownBy(() -> workflowService.enqueueAnalysis(complaintId, key(), version + 1))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("All attachments must pass");
	}

	@Test
	void enforcesReviewApprovalSeparationAndManualCompletion() {
		verifyOfficialWasteEvidence();
		String idempotencyKey = key();
		JsonNode first = createComplaint("Illegal dumping and waste bags require inspection.", "Pilot alley", idempotencyKey);
		JsonNode duplicate = createComplaint("This body is ignored for the duplicate key.", "Other", idempotencyKey);
		assertThat(duplicate.path("data").path("id").asText()).isEqualTo(first.path("data").path("id").asText());

		String complaintId = first.path("data").path("id").asText();
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", first.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode triageDetail = confirmFirstDepartment(complaintId);
		String draftRunKey = key();
		JsonNode draftJob = startRun(
				complaintId,
				"draft-runs",
				triageDetail.path("complaint").path("version").asLong(),
				draftRunKey
		);
		JsonNode terminalDraftJob = waitForJob(draftJob.path("data").path("id").asText());
		assertThat(terminalDraftJob.path("status").asText())
				.withFailMessage(terminalDraftJob.toPrettyString())
				.isEqualTo("SUCCEEDED");
		JsonNode replayedDraftJob = startRun(
				complaintId,
				"draft-runs",
				triageDetail.path("complaint").path("version").asLong(),
				draftRunKey
		);
		assertThat(replayedDraftJob.path("data").path("id").asText())
				.isEqualTo(draftJob.path("data").path("id").asText());

		JsonNode draftDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		long draftId = draftDetail.path("draft").path("id").asLong();
		assertThat(draftDetail.path("complaint").path("status").asText()).isEqualTo("DRAFT_REVIEW");
		assertThat(draftDetail.path("draft").path("draftText").asText()).contains("Staff review required");
		assertThat(draftDetail.path("evidence").size()).isGreaterThanOrEqualTo(1);
		assertThat(draftDetail.path("evidence").get(0).has("score")).isFalse();
		assertThat(draftDetail.path("evidence").get(0).path("legalBasis").asText()).isNotBlank();
		assertThat(draftDetail.path("evidence").get(0).path("sourceVersion").asText()).isNotBlank();
		assertThat(draftDetail.path("evidence").get(0).path("contentHash").asText()).isNotBlank();
		assertThat(draftDetail.path("evidence").get(0).path("sourceStatus").asText()).isEqualTo("VERIFIED_OFFICIAL");
		assertThat(draftDetail.path("draftClaims").size()).isGreaterThanOrEqualTo(4);
		assertThat(draftDetail.path("draftClaims").get(0).path("evidenceSourceIds").size()).isGreaterThanOrEqualTo(1);
		assertThat(draftDetail.path("verificationResults").size()).isGreaterThanOrEqualTo(6);
		assertThat(draftDetail.path("aiRuns").size()).isGreaterThanOrEqualTo(2);
		assertThat(draftClaimRepository.count()).isGreaterThanOrEqualTo(4);

		String reviewKey = key();
		JsonNode reviewed = decide("/api/v1/drafts/" + draftId + "/reviews", 0, true, reviewKey);
		assertThat(reviewed.path("data").path("status").asText()).isEqualTo("APPROVAL_PENDING");
		assertThat(reviewed.path("data").path("reviewedBy").asText()).isEqualTo("system-reviewer");
		JsonNode replayedReview = decide("/api/v1/drafts/" + draftId + "/reviews", 0, true, reviewKey);
		assertThat(replayedReview.path("data").path("status").asText()).isEqualTo("APPROVAL_PENDING");

		ResponseEntity<JsonNode> reusedReviewKey = post(
				"/api/v1/drafts/" + draftId + "/approvals",
				Map.of("approved", true, "notes", "must conflict"),
				reviewed.path("data").path("version").asLong(),
				reviewKey
		);
		assertThat(reusedReviewKey.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		String approvalKey = key();
		JsonNode approved = decide(
				"/api/v1/drafts/" + draftId + "/approvals",
				reviewed.path("data").path("version").asLong(),
				true,
				approvalKey
		);
		assertThat(approved.path("data").path("status").asText()).isEqualTo("APPROVED");
		assertThat(approved.path("data").path("approvedBy").asText()).isEqualTo("system-approver");
		JsonNode replayedApproval = decide(
				"/api/v1/drafts/" + draftId + "/approvals",
				reviewed.path("data").path("version").asLong(),
				true,
				approvalKey
		);
		assertThat(replayedApproval.path("data").path("status").asText()).isEqualTo("APPROVED");

		JsonNode approvedDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		String completionKey = key();
		JsonNode completed = postWithoutBody(
				"/api/v1/complaints/" + complaintId + "/complete",
				approvedDetail.path("complaint").path("version").asLong(),
				completionKey
		);
		assertThat(completed.path("data").path("status").asText()).isEqualTo("COMPLETED");
		JsonNode replayedCompletion = postWithoutBody(
				"/api/v1/complaints/" + complaintId + "/complete",
				approvedDetail.path("complaint").path("version").asLong(),
				completionKey
		);
		assertThat(replayedCompletion.path("data").path("status").asText()).isEqualTo("COMPLETED");
		assertThat(workflowAuditEventRepository.count()).isGreaterThanOrEqualTo(5);
		assertThat(getOk("/api/v1/complaints/" + complaintId)
				.path("data").path("humanReviews").size()).isEqualTo(2);
		ResponseEntity<JsonNode> analysisAfterCompletion = post(
				"/api/v1/complaints/" + complaintId + "/analysis-runs",
				null,
				completed.path("data").path("version").asLong(),
				key()
		);
		assertThat(analysisAfterCompletion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void unknownLocationRequiresHumanConfirmationAndLegacyMutationGetsAreGone() {
		JsonNode created = createComplaint("Road pothole requires inspection.", null, key());
		String complaintId = created.path("data").path("id").asText();
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode detail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(detail.path("complaint").path("workflowBlocker").asText()).isEqualTo("NEEDS_LOCATION");

		ResponseEntity<JsonNode> draftAttempt = post(
				"/api/v1/complaints/" + complaintId + "/draft-runs",
				null,
				detail.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(draftAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		String issueId = detail.path("issues").get(0).path("id").asText();
		ResponseEntity<JsonNode> confirmed = post(
				"/api/v1/issues/" + issueId + "/location-confirmations",
				Map.of("locationText", "Confirmed pilot road"),
				detail.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(confirmed.getBody().path("data").path("workflowBlocker").isNull()).isTrue();

		ResponseEntity<JsonNode> legacyAnalysis = restTemplate.getForEntity(
				url("/api/complaints/" + complaintId + "/analysis"),
				JsonNode.class
		);
		assertThat(legacyAnalysis.getStatusCode()).isEqualTo(HttpStatus.GONE);
		ResponseEntity<JsonNode> legacyGeoJson = restTemplate.getForEntity(
				url("/api/complaints/" + complaintId + "/geojson"),
				JsonNode.class
		);
		assertThat(legacyGeoJson.getStatusCode()).isEqualTo(HttpStatus.GONE);
		ResponseEntity<JsonNode> legacyRag = restTemplate.getForEntity(
				url("/api/complaints/" + complaintId + "/rag-contexts"),
				JsonNode.class
		);
		assertThat(legacyRag.getStatusCode()).isEqualTo(HttpStatus.GONE);
	}

	@Test
	void staleVersionIsRejected() {
		JsonNode created = createComplaint("Illegal parking complaint.", "Pilot road", key());
		String complaintId = created.path("data").path("id").asText();
		ResponseEntity<JsonNode> response = post(
				"/api/v1/complaints/" + complaintId + "/analysis-runs",
				null,
				99L,
				key()
		);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("WORKFLOW_CONFLICT");
	}

	@Test
	void concurrentCreateWithSameIdempotencyKeyDoesNotCreateDuplicates() throws Exception {
		long before = complaintRepository.count();
		String idempotencyKey = key();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			var request = (java.util.concurrent.Callable<ResponseEntity<JsonNode>>) () -> {
				start.await();
				return post(
						"/api/v1/complaints",
						Map.of(
								"sourceChannel", "WEB",
								"rawText", "Concurrent idempotency test complaint.",
								"locationText", "Pilot road"
						),
						null,
						idempotencyKey
				);
			};
			Future<ResponseEntity<JsonNode>> firstFuture = executor.submit(request);
			Future<ResponseEntity<JsonNode>> secondFuture = executor.submit(request);
			start.countDown();
			ResponseEntity<JsonNode> first = firstFuture.get(10, TimeUnit.SECONDS);
			ResponseEntity<JsonNode> second = secondFuture.get(10, TimeUnit.SECONDS);
			List<HttpStatus> statuses = List.of(
					HttpStatus.valueOf(first.getStatusCode().value()),
					HttpStatus.valueOf(second.getStatusCode().value())
			);

			assertThat(statuses).contains(HttpStatus.CREATED);
			assertThat(statuses).allMatch(status -> status == HttpStatus.CREATED || status == HttpStatus.CONFLICT);
			assertThat(complaintRepository.count()).isEqualTo(before + 1);
			if (first.getStatusCode() == HttpStatus.CREATED && second.getStatusCode() == HttpStatus.CREATED) {
				assertThat(first.getBody().path("data").path("id").asText())
						.isEqualTo(second.getBody().path("data").path("id").asText());
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void complaintOutsideSyntheticPilotTypesRequiresJurisdictionReview() {
		JsonNode created = createComplaint("Please review this general request.", "Pilot office", key());
		String complaintId = created.path("data").path("id").asText();
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode detail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(detail.path("complaint").path("workflowBlocker").asText()).isEqualTo("NEEDS_JURISDICTION");
		assertThat(detail.path("issues").get(0).path("jurisdictionStatus").asText()).isEqualTo("NEEDS_JURISDICTION");
	}

	@Test
	void locationConfirmationDoesNotClearUnresolvedJurisdiction() {
		JsonNode created = createComplaint("Please review this unsupported general request.", null, key());
		String complaintId = created.path("data").path("id").asText();
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode detail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(detail.path("complaint").path("workflowBlocker").asText()).isEqualTo("NEEDS_LOCATION");
		String issueId = detail.path("issues").get(0).path("id").asText();
		ResponseEntity<JsonNode> confirmed = post(
				"/api/v1/issues/" + issueId + "/location-confirmations",
				Map.of("locationText", "Confirmed pilot office"),
				detail.path("complaint").path("version").asLong(),
				key()
		);

		assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(confirmed.getBody().path("data").path("workflowBlocker").asText()).isEqualTo("NEEDS_JURISDICTION");
	}

	@Test
	void getRequestsDoNotWriteRequestAuditRows() {
		JsonNode created = createComplaint("Read-only request audit check.", "Pilot road", key());
		String complaintId = created.path("data").path("id").asText();
		long before = auditLogRepository.count();

		getOk("/api/v1/complaints/" + complaintId);

		assertThat(auditLogRepository.count()).isEqualTo(before);
	}

	@Test
	void blocksDraftWhenOverlappingOfficialEvidenceConflicts() {
		verifyOfficialWasteEvidence();
		KnowledgeDocument conflicting = new KnowledgeDocument(
				DocumentType.LAW,
				"Conflicting official waste provision",
				"Official synthetic test source",
				"https://example.invalid/conflicting-official-test",
				"Illegal dumping reports must not receive a field inspection.",
				"waste,dumping,garbage",
				"Waste handling reference"
		);
		conflicting.verifyForTest(
				KnowledgePurpose.OFFICIAL_LAW,
				KnowledgeVerificationStatus.VERIFIED_OFFICIAL,
				"NATIONAL",
				LocalDate.now().minusYears(1),
				null
		);
		knowledgeDocumentRepository.save(conflicting);

		JsonNode created = createComplaint("Illegal dumping requires inspection.", "Pilot alley", key());
		String complaintId = created.path("data").path("id").asText();
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");
		JsonNode detail = confirmFirstDepartment(complaintId);
		JsonNode draftJob = startRun(
				complaintId,
				"draft-runs",
				detail.path("complaint").path("version").asLong(),
				key()
		);

		assertThat(waitForJob(draftJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("BLOCKED");
		JsonNode blockedDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(blockedDetail.path("complaint").path("workflowBlocker").asText()).isEqualTo("CONFLICT_DETECTED");
		assertThat(blockedDetail.path("draft").isNull()).isTrue();
		assertThat(blockedDetail.path("evidence").size()).isGreaterThanOrEqualTo(2);
		for (JsonNode item : blockedDetail.path("evidence")) {
			assertThat(item.path("supportsClaim").asBoolean()).isFalse();
			assertThat(item.path("sourceVersion").asText()).isNotBlank();
			assertThat(item.path("contentHash").asText()).isNotBlank();
		}
	}

	@Test
	void blocksDraftWhenOfficialEvidenceContentHashDoesNotMatchSnapshotContent() {
		verifyOfficialWasteEvidence();
		KnowledgeDocument document = knowledgeDocumentRepository.findByTitle("Legacy waste handling summary").orElseThrow();
		ReflectionTestUtils.setField(document, "contentHash", "mismatched-content-hash");
		knowledgeDocumentRepository.save(document);

		JsonNode created = createComplaint("Illegal dumping requires inspection.", "Pilot alley", key());
		String complaintId = created.path("data").path("id").asText();
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");
		JsonNode detail = confirmFirstDepartment(complaintId);
		JsonNode draftJob = startRun(
				complaintId,
				"draft-runs",
				detail.path("complaint").path("version").asLong(),
				key()
		);

		assertThat(waitForJob(draftJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("BLOCKED");
		JsonNode blockedDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(blockedDetail.path("complaint").path("workflowBlocker").asText()).isEqualTo("EVIDENCE_INSUFFICIENT");
		assertThat(blockedDetail.path("draft").isNull()).isTrue();
		assertThat(blockedDetail.path("evidence").size()).isGreaterThanOrEqualTo(1);
		assertThat(blockedDetail.path("evidence").get(0).path("supportsClaim").asBoolean()).isFalse();
	}

	@Test
	void irrelevantVerifiedLawDoesNotContaminateDraftEvidence() {
		verifyOfficialWasteEvidence();
		KnowledgeDocument unrelated = new KnowledgeDocument(
				DocumentType.LAW,
				"Unrelated national road provision",
				"Official synthetic test source",
				"https://example.invalid/unrelated-road-test",
				"Road paving inspection requirements apply to pothole repairs.",
				"road,pothole,paving",
				"Road handling reference"
		);
		unrelated.verifyForTest(
				KnowledgePurpose.OFFICIAL_LAW,
				KnowledgeVerificationStatus.VERIFIED_OFFICIAL,
				"NATIONAL",
				LocalDate.now().minusYears(1),
				null
		);
		knowledgeDocumentRepository.save(unrelated);

		JsonNode created = createComplaint("Illegal dumping requires inspection.", "Pilot alley", key());
		String complaintId = created.path("data").path("id").asText();
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");
		JsonNode triage = confirmFirstDepartment(complaintId);
		JsonNode draftJob = startRun(
				complaintId,
				"draft-runs",
				triage.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(waitForJob(draftJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode evidence = getOk("/api/v1/complaints/" + complaintId).path("data").path("evidence");
		for (JsonNode item : evidence) {
			assertThat(item.path("title").asText()).doesNotContain("Unrelated national road provision");
		}
	}

	@Test
	void rejectedDraftCanBeRegeneratedForAnotherReview() {
		verifyOfficialWasteEvidence();
		JsonNode created = createComplaint("Illegal dumping requires a revised reply.", "Pilot alley", key());
		String complaintId = created.path("data").path("id").asText();
		JsonNode analysisJob = startRun(complaintId, "analysis-runs", created.path("data").path("version").asLong(), key());
		assertThat(waitForJob(analysisJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode triage = confirmFirstDepartment(complaintId);
		JsonNode firstDraftJob = startRun(
				complaintId,
				"draft-runs",
				triage.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(waitForJob(firstDraftJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode firstDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		long firstDraftId = firstDetail.path("draft").path("id").asLong();
		JsonNode rejected = decide("/api/v1/drafts/" + firstDraftId + "/reviews", 0, false, key());
		assertThat(rejected.path("data").path("status").asText()).isEqualTo("REJECTED");

		JsonNode rejectedDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		JsonNode replacementJob = startRun(
				complaintId,
				"draft-runs",
				rejectedDetail.path("complaint").path("version").asLong(),
				key()
		);
		assertThat(waitForJob(replacementJob.path("data").path("id").asText()).path("status").asText()).isEqualTo("SUCCEEDED");

		JsonNode replacementDetail = getOk("/api/v1/complaints/" + complaintId).path("data");
		assertThat(replacementDetail.path("draft").path("id").asLong()).isNotEqualTo(firstDraftId);
		assertThat(replacementDetail.path("complaint").path("status").asText()).isEqualTo("DRAFT_REVIEW");
		assertThat(replacementDetail.path("evidence").size()).isEqualTo(1);
	}

	private void verifyOfficialWasteEvidence() {
		var document = knowledgeDocumentRepository.findByTitle("Legacy waste handling summary").orElseThrow();
		document.verifyForTest(
				KnowledgePurpose.OFFICIAL_LAW,
				KnowledgeVerificationStatus.VERIFIED_OFFICIAL,
				"NATIONAL",
				LocalDate.now().minusYears(1),
				null
		);
		knowledgeDocumentRepository.save(document);
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
		assertThat(confirmed.path("verificationResults").findValuesAsText("ruleCode"))
				.contains("DEPARTMENT_SELECTION");
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
