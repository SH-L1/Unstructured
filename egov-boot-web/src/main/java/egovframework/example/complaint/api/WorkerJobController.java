package egovframework.example.complaint.api;

import egovframework.example.complaint.api.dto.ApiResponse;
import egovframework.example.complaint.api.dto.ProcessingJobResponse;
import egovframework.example.complaint.api.dto.WorkerClaimRequest;
import egovframework.example.complaint.api.dto.WorkerFailureRequest;
import egovframework.example.complaint.api.dto.WorkerJobResponse;
import egovframework.example.complaint.api.dto.WorkerResultRequest;
import egovframework.example.complaint.api.dto.WorkerSupportResultRequest;
import egovframework.example.complaint.service.WorkerJobService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/worker/jobs")
public class WorkerJobController {

	private final WorkerJobService workerJobService;

	public WorkerJobController(WorkerJobService workerJobService) {
		this.workerJobService = workerJobService;
	}

	@PostMapping("/claim")
	public ResponseEntity<ApiResponse<WorkerJobResponse>> claim(@Valid @RequestBody WorkerClaimRequest request) {
		WorkerJobResponse claimed = workerJobService.claim(request);
		return claimed == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(ApiResponse.ok(claimed));
	}

	@PostMapping("/{id}/results")
	public ApiResponse<ProcessingJobResponse> submitResult(
			@PathVariable UUID id,
			@Valid @RequestBody WorkerResultRequest request
	) {
		return ApiResponse.ok(workerJobService.submitResult(id, request));
	}

	@PostMapping("/{id}/support-results")
	public ApiResponse<ProcessingJobResponse> submitSupportResult(
			@PathVariable UUID id,
			@Valid @RequestBody WorkerSupportResultRequest request
	) {
		return ApiResponse.ok(workerJobService.submitSupportResult(id, request));
	}

	@PostMapping("/{id}/failures")
	public ApiResponse<ProcessingJobResponse> submitFailure(
			@PathVariable UUID id,
			@Valid @RequestBody WorkerFailureRequest request
	) {
		return ApiResponse.ok(workerJobService.submitFailure(id, request));
	}
}
