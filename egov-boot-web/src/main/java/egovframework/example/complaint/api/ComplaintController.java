package egovframework.example.complaint.api;

import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.api.dto.DraftResponse;
import egovframework.example.complaint.service.ComplaintService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

	private final ComplaintService complaintService;

	public ComplaintController(ComplaintService complaintService) {
		this.complaintService = complaintService;
	}

	@PostMapping
	public ResponseEntity<ComplaintResponse> create(@Valid @RequestBody CreateComplaintRequest request) {
		ComplaintResponse response = complaintService.create(request);
		return ResponseEntity
				.created(URI.create("/api/complaints/" + response.id()))
				.body(response);
	}

	@GetMapping
	public List<ComplaintResponse> findAll() {
		return complaintService.findAll();
	}

	@GetMapping("/{id}")
	public ComplaintResponse findById(@PathVariable UUID id) {
		return complaintService.findById(id);
	}

	@GetMapping("/{id}/analysis")
	public ComplaintAnalysisResponse analyze(@PathVariable UUID id) {
		return complaintService.analyze(id);
	}

	@GetMapping("/{id}/draft")
	public DraftResponse draft(@PathVariable UUID id) {
		return complaintService.generateDraft(id);
	}
}
