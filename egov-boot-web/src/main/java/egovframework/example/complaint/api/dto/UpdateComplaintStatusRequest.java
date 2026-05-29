package egovframework.example.complaint.api.dto;

import egovframework.example.complaint.domain.ComplaintStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateComplaintStatusRequest(
		@NotNull ComplaintStatus status
) {
}
