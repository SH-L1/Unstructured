package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "attachment_analysis")
public class AttachmentAnalysis extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attachment_id", nullable = false, unique = true)
	private ComplaintAttachment attachment;

	@Column(nullable = false, length = 40)
	private String quarantineStatus;

	@Column(length = 100)
	private String detectedType;

	@Column(nullable = false, length = 40)
	private String malwareStatus;

	@Column(nullable = false)
	private boolean exifRemoved;

	@Column(columnDefinition = "text")
	private String ocrText;

	@Column(nullable = false, columnDefinition = "text")
	private String piiFindings;

	@Column(nullable = false)
	private boolean approvedForAi;

	@Column(length = 500)
	private String derivedStorageReference;

	protected AttachmentAnalysis() {
	}

	public AttachmentAnalysis(ComplaintAttachment attachment, String detectedType) {
		this.attachment = attachment;
		this.detectedType = detectedType;
		this.quarantineStatus = "QUARANTINED";
		this.malwareStatus = "PENDING_SCAN";
		this.piiFindings = "PENDING_REDACTION";
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public boolean isApprovedForAi() {
		return approvedForAi;
	}

	public String getOcrText() {
		return ocrText;
	}
}
