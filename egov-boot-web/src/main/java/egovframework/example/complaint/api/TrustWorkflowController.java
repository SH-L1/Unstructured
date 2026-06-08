package egovframework.example.complaint.api;

import egovframework.example.complaint.api.dto.ApiResponse;
import egovframework.example.complaint.api.dto.AttachmentResponse;
import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.api.dto.DepartmentConfirmationRequest;
import egovframework.example.complaint.api.dto.LocationConfirmationRequest;
import egovframework.example.complaint.api.dto.ProcessingJobResponse;
import egovframework.example.complaint.api.dto.ReviewDecisionRequest;
import egovframework.example.complaint.api.dto.TrustComplaintDetailResponse;
import egovframework.example.complaint.api.dto.TrustDraftResponse;
import egovframework.example.complaint.service.TrustWorkflowService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class TrustWorkflowController {

	private final TrustWorkflowService workflowService;

	public TrustWorkflowController(TrustWorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@PostMapping("/complaints")
	public ResponseEntity<ApiResponse<ComplaintResponse>> create(
			@Valid @RequestBody CreateComplaintRequest request,
			@RequestHeader("Idempotency-Key") String idempotencyKey
	) {
		ComplaintResponse response = workflowService.create(request, idempotencyKey);
		return ResponseEntity.created(URI.create("/api/v1/complaints/" + response.id()))
				.eTag(String.valueOf(response.version()))
				.body(ApiResponse.created(response));
	}

	@GetMapping("/complaints/{id}")
	public ResponseEntity<ApiResponse<TrustComplaintDetailResponse>> detail(@PathVariable UUID id) {
		TrustComplaintDetailResponse response = workflowService.detail(id);
		return ResponseEntity.ok()
				.eTag(String.valueOf(response.complaint().version()))
				.body(ApiResponse.ok(response));
	}

	@PostMapping(value = "/complaints/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<AttachmentResponse>> addAttachment(
			@PathVariable UUID id,
			@RequestParam("file") MultipartFile file,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		AttachmentResponse response = workflowService.addAttachment(id, file, idempotencyKey, parseVersion(ifMatch));
		return ResponseEntity.created(URI.create("/api/v1/complaints/" + id + "/attachments/" + response.id()))
				.body(ApiResponse.created(response));
	}

	@GetMapping("/complaints/{id}/attachments")
	public ApiResponse<List<AttachmentResponse>> attachments(@PathVariable UUID id) {
		return ApiResponse.ok(workflowService.findAttachments(id));
	}

	@DeleteMapping("/complaints/{id}/attachments/{attachmentId}")
	public ResponseEntity<Void> deleteAttachment(
			@PathVariable UUID id,
			@PathVariable UUID attachmentId,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		workflowService.deleteAttachment(id, attachmentId, idempotencyKey, parseVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/complaints/{id}/analysis-runs")
	public ResponseEntity<ApiResponse<ProcessingJobResponse>> analysisRun(
			@PathVariable UUID id,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		ProcessingJobResponse response = workflowService.enqueueAnalysis(id, idempotencyKey, parseVersion(ifMatch));
		return ResponseEntity.accepted()
				.location(URI.create("/api/v1/runs/" + response.id()))
				.body(ApiResponse.created(response));
	}

	@PostMapping("/complaints/{id}/draft-runs")
	public ResponseEntity<ApiResponse<ProcessingJobResponse>> draftRun(
			@PathVariable UUID id,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		ProcessingJobResponse response = workflowService.enqueueDraft(id, idempotencyKey, parseVersion(ifMatch));
		return ResponseEntity.accepted()
				.location(URI.create("/api/v1/runs/" + response.id()))
				.body(ApiResponse.created(response));
	}

	@GetMapping("/runs/{id}")
	public ApiResponse<ProcessingJobResponse> run(@PathVariable UUID id) {
		return ApiResponse.ok(workflowService.findJob(id));
	}

	@PostMapping("/issues/{id}/location-confirmations")
	public ResponseEntity<ApiResponse<ComplaintResponse>> confirmLocation(
			@PathVariable UUID id,
			@Valid @RequestBody LocationConfirmationRequest request,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		ComplaintResponse response = workflowService.confirmLocation(id, request.locationText(), idempotencyKey, parseVersion(ifMatch));
		return ResponseEntity.ok().eTag(String.valueOf(response.version())).body(ApiResponse.ok(response));
	}

	@PostMapping("/issues/{id}/department-confirmations")
	public ResponseEntity<ApiResponse<TrustComplaintDetailResponse>> confirmDepartment(
			@PathVariable UUID id,
			@Valid @RequestBody DepartmentConfirmationRequest request,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		TrustComplaintDetailResponse response = workflowService.confirmDepartment(
				id, request.departmentCode(), idempotencyKey, parseVersion(ifMatch)
		);
		return ResponseEntity.ok()
				.eTag(String.valueOf(response.complaint().version()))
				.body(ApiResponse.ok(response));
	}

	@PostMapping("/drafts/{id}/reviews")
	public ResponseEntity<ApiResponse<TrustDraftResponse>> review(
			@PathVariable Long id,
			@Valid @RequestBody ReviewDecisionRequest request,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		TrustDraftResponse response = workflowService.review(id, request.approved(), request.notes(), idempotencyKey, parseVersion(ifMatch));
		return ResponseEntity.ok().eTag(String.valueOf(response.version())).body(ApiResponse.ok(response));
	}

	@PostMapping("/drafts/{id}/approvals")
	public ResponseEntity<ApiResponse<TrustDraftResponse>> approve(
			@PathVariable Long id,
			@Valid @RequestBody ReviewDecisionRequest request,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		TrustDraftResponse response = workflowService.approve(id, request.approved(), request.notes(), idempotencyKey, parseVersion(ifMatch));
		return ResponseEntity.ok().eTag(String.valueOf(response.version())).body(ApiResponse.ok(response));
	}

	@PostMapping("/complaints/{id}/complete")
	public ResponseEntity<ApiResponse<ComplaintResponse>> complete(
			@PathVariable UUID id,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
	) {
		ComplaintResponse response = workflowService.complete(id, idempotencyKey, parseVersion(ifMatch));
		return ResponseEntity.ok().eTag(String.valueOf(response.version())).body(ApiResponse.ok(response));
	}

	private long parseVersion(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			throw new IllegalArgumentException("If-Match header is required");
		}
		String normalized = ifMatch.trim().replace("W/", "").replace("\"", "");
		try {
			return Long.parseLong(normalized);
		}
		catch (NumberFormatException exception) {
			throw new IllegalArgumentException("If-Match must contain a numeric entity version", exception);
		}
	}
}
