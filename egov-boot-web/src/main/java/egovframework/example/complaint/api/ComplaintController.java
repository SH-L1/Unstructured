package egovframework.example.complaint.api;

import egovframework.example.complaint.api.dto.ApiResponse;
import egovframework.example.complaint.api.dto.AttachmentResponse;
import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.api.dto.DraftResponse;
import egovframework.example.complaint.api.dto.PageResponse;
import egovframework.example.complaint.api.dto.RagContextResponse;
import egovframework.example.complaint.api.dto.UpdateComplaintStatusRequest;
import egovframework.example.complaint.api.dto.UpdateDraftRequest;
import egovframework.example.complaint.domain.ComplaintStatus;
import egovframework.example.complaint.service.ComplaintService;
import egovframework.example.complaint.service.ComplaintService.DownloadedAttachment;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

	private final ComplaintService complaintService;

	public ComplaintController(ComplaintService complaintService) {
		this.complaintService = complaintService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ComplaintResponse>> create(@Valid @RequestBody CreateComplaintRequest request) {
		ComplaintResponse response = complaintService.create(request);
		return ResponseEntity
				.created(URI.create("/api/complaints/" + response.id()))
				.body(ApiResponse.created(response));
	}

	@GetMapping
	public ApiResponse<PageResponse<ComplaintResponse>> findAll(
			@RequestParam(required = false) ComplaintStatus status,
			@RequestParam(required = false) String department,
			@RequestParam(required = false) String urgency,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
		return ApiResponse.ok(PageResponse.from(complaintService.findAll(status, department, urgency, pageable)));
	}

	@GetMapping("/{id}")
	public ApiResponse<ComplaintResponse> findById(@PathVariable UUID id) {
		return ApiResponse.ok(complaintService.findById(id));
	}

	@PatchMapping("/{id}/status")
	public ApiResponse<ComplaintResponse> updateStatus(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateComplaintStatusRequest request
	) {
		return ApiResponse.ok(complaintService.updateStatus(id, request.status()));
	}

	@GetMapping("/{id}/analysis")
	public ApiResponse<ComplaintAnalysisResponse> analyze(@PathVariable UUID id) {
		return ApiResponse.ok(complaintService.analyze(id));
	}

	@GetMapping("/{id}/draft")
	public ApiResponse<DraftResponse> draft(@PathVariable UUID id) {
		return ApiResponse.ok(complaintService.generateDraft(id));
	}

	@PutMapping("/{id}/draft")
	public ApiResponse<DraftResponse> updateDraft(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateDraftRequest request
	) {
		return ApiResponse.ok(complaintService.updateDraft(id, request.draftText()));
	}

	@GetMapping("/{id}/rag-contexts")
	public ApiResponse<List<RagContextResponse>> ragContexts(@PathVariable UUID id) {
		return ApiResponse.ok(complaintService.findRagContexts(id));
	}

	@GetMapping("/{id}/geojson")
	public ApiResponse<String> geoJson(@PathVariable UUID id) {
		return ApiResponse.ok(complaintService.findGeoJson(id));
	}

	@PostMapping("/{id}/attachments")
	public ApiResponse<AttachmentResponse> addAttachment(
			@PathVariable UUID id,
			@RequestParam("file") MultipartFile file
	) {
		return ApiResponse.created(complaintService.addAttachment(id, file));
	}

	@GetMapping("/{id}/attachments")
	public ApiResponse<List<AttachmentResponse>> attachments(@PathVariable UUID id) {
		return ApiResponse.ok(complaintService.findAttachments(id));
	}

	@GetMapping("/{id}/attachments/{attachmentId}")
	public ResponseEntity<byte[]> downloadAttachment(
			@PathVariable UUID id,
			@PathVariable UUID attachmentId
	) {
		DownloadedAttachment attachment = complaintService.downloadAttachment(id, attachmentId);
		String contentType = attachment.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : attachment.contentType();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, complaintService.attachmentDisposition(attachment.originalFilename()))
				.contentType(MediaType.parseMediaType(contentType))
				.body(attachment.bytes());
	}

	@DeleteMapping("/{id}/attachments/{attachmentId}")
	public ResponseEntity<Void> deleteAttachment(
			@PathVariable UUID id,
			@PathVariable UUID attachmentId
	) {
		complaintService.deleteAttachment(id, attachmentId);
		return ResponseEntity.noContent().build();
	}
}
