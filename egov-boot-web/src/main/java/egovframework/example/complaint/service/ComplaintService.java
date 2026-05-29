package egovframework.example.complaint.service;

import egovframework.example.complaint.api.dto.AttachmentResponse;
import egovframework.example.complaint.api.dto.ComplaintAnalysisResponse;
import egovframework.example.complaint.api.dto.ComplaintResponse;
import egovframework.example.complaint.api.dto.CreateComplaintRequest;
import egovframework.example.complaint.api.dto.DraftResponse;
import egovframework.example.complaint.api.dto.RagContextResponse;
import egovframework.example.complaint.domain.Complaint;
import egovframework.example.complaint.domain.ComplaintAttachment;
import egovframework.example.complaint.domain.ComplaintStatus;
import egovframework.example.complaint.repository.ComplaintAttachmentRepository;
import egovframework.example.complaint.repository.ComplaintRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ComplaintService {

	private final ComplaintRepository complaintRepository;
	private final ComplaintAttachmentRepository complaintAttachmentRepository;
	private final ComplaintAnalysisService complaintAnalysisService;
	private final DraftService draftService;
	private final RagSearchService ragSearchService;
	private final FileStorageService fileStorageService;

	public ComplaintService(
			ComplaintRepository complaintRepository,
			ComplaintAttachmentRepository complaintAttachmentRepository,
			ComplaintAnalysisService complaintAnalysisService,
			DraftService draftService,
			RagSearchService ragSearchService,
			FileStorageService fileStorageService
	) {
		this.complaintRepository = complaintRepository;
		this.complaintAttachmentRepository = complaintAttachmentRepository;
		this.complaintAnalysisService = complaintAnalysisService;
		this.draftService = draftService;
		this.ragSearchService = ragSearchService;
		this.fileStorageService = fileStorageService;
	}

	@Transactional
	public ComplaintResponse create(CreateComplaintRequest request) {
		String sourceChannel = normalizeSourceChannel(request.sourceChannel());
		Complaint complaint = new Complaint(sourceChannel, request.rawText(), request.locationText());
		return ComplaintResponse.from(complaintRepository.save(complaint));
	}

	@Transactional(readOnly = true)
	public Page<ComplaintResponse> findAll(
			ComplaintStatus status,
			String department,
			String urgency,
			Pageable pageable
	) {
		return complaintRepository.findAll(filterBy(status, department, urgency), pageable)
				.map(ComplaintResponse::from);
	}

	@Transactional(readOnly = true)
	public ComplaintResponse findById(UUID id) {
		return ComplaintResponse.from(getComplaint(id));
	}

	@Transactional
	public ComplaintResponse updateStatus(UUID id, ComplaintStatus status) {
		Complaint complaint = getComplaint(id);
		complaint.changeStatus(status);
		return ComplaintResponse.from(complaint);
	}

	@Transactional
	public ComplaintAnalysisResponse analyze(UUID id) {
		Complaint complaint = getComplaint(id);
		ComplaintAnalysisResponse analysis = complaintAnalysisService.analyze(complaint);
		complaint.applyAnalysis(analysis.intent(), analysis.urgency(), analysis.sentiment(), analysis.department());
		return analysis;
	}

	@Transactional
	public DraftResponse generateDraft(UUID id) {
		Complaint complaint = getComplaint(id);
		ComplaintAnalysisResponse analysis = ensureAnalysis(complaint);
		DraftResponse draft = draftService.generateDraft(complaint, analysis);
		complaint.updateDraft(draft.draftText());
		return draft;
	}

	@Transactional
	public DraftResponse updateDraft(UUID id, String draftText) {
		Complaint complaint = getComplaint(id);
		complaint.updateDraft(draftText);
		return draftService.updateDraft(complaint, draftText);
	}

	@Transactional(readOnly = true)
	public List<RagContextResponse> findRagContexts(UUID id) {
		return ragSearchService.searchContexts(getComplaint(id));
	}

	@Transactional(readOnly = true)
	public String findGeoJson(UUID id) {
		Complaint complaint = getComplaint(id);
		return complaintAnalysisService.analyze(complaint).geoJson();
	}

	@Transactional
	public AttachmentResponse addAttachment(UUID id, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Attachment file is required");
		}
		Complaint complaint = getComplaint(id);
		try {
			StoredFile storedFile = fileStorageService.store(
					file.getOriginalFilename(),
					file.getContentType(),
					file.getSize(),
					file.getInputStream()
			);
			ComplaintAttachment attachment = new ComplaintAttachment(
					complaint,
					storedFile.originalFilename(),
					storedFile.contentType(),
					storedFile.size(),
					storedFile.storageKey()
			);
			return AttachmentResponse.from(complaintAttachmentRepository.save(attachment));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read attachment file", exception);
		}
	}

	@Transactional(readOnly = true)
	public List<AttachmentResponse> findAttachments(UUID id) {
		getComplaint(id);
		return complaintAttachmentRepository.findByComplaintIdOrderByCreatedAtDesc(id).stream()
				.map(AttachmentResponse::from)
				.toList();
	}

	private ComplaintAnalysisResponse ensureAnalysis(Complaint complaint) {
		if (StringUtils.hasText(complaint.getIntent())
				&& StringUtils.hasText(complaint.getUrgency())
				&& StringUtils.hasText(complaint.getDepartment())) {
			return new ComplaintAnalysisResponse(
					complaint.getId(),
					complaint.getIntent(),
					complaint.getUrgency(),
					complaint.getSentiment(),
					complaint.getDepartment(),
					complaint.getLocationText(),
					complaintAnalysisService.analyze(complaint).geoJson()
			);
		}
		ComplaintAnalysisResponse analysis = complaintAnalysisService.analyze(complaint);
		complaint.applyAnalysis(analysis.intent(), analysis.urgency(), analysis.sentiment(), analysis.department());
		return analysis;
	}

	private Complaint getComplaint(UUID id) {
		return complaintRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + id));
	}

	private Specification<Complaint> filterBy(ComplaintStatus status, String department, String urgency) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(criteriaBuilder.equal(root.get("status"), status));
			}
			if (StringUtils.hasText(department)) {
				predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("department")), department.toLowerCase(Locale.ROOT)));
			}
			if (StringUtils.hasText(urgency)) {
				predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("urgency")), urgency.toLowerCase(Locale.ROOT)));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private String normalizeSourceChannel(String sourceChannel) {
		if (sourceChannel == null || sourceChannel.isBlank()) {
			return "WEB";
		}
		return sourceChannel.trim().toUpperCase(Locale.ROOT);
	}
}
