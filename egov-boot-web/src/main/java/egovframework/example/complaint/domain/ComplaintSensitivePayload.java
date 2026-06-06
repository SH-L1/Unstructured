package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "complaint_sensitive_payloads")
public class ComplaintSensitivePayload extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@OneToOne(optional = false)
	@JoinColumn(name = "complaint_id", nullable = false, unique = true)
	private Complaint complaint;

	@Column(nullable = false, length = 500)
	private String rawStorageReference;

	@Column(nullable = false, columnDefinition = "text")
	private String redactedText;

	@Column(nullable = false, columnDefinition = "text")
	private String piiFindings;

	protected ComplaintSensitivePayload() {
	}

	public ComplaintSensitivePayload(Complaint complaint, String rawStorageReference, String redactedText, String piiFindings) {
		this.complaint = complaint;
		this.rawStorageReference = rawStorageReference;
		this.redactedText = redactedText;
		this.piiFindings = piiFindings;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public String getRawStorageReference() {
		return rawStorageReference;
	}
}
