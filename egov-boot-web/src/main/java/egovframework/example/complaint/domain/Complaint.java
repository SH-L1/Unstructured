package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "complaints")
public class Complaint extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 60, unique = true)
	private String receiptNumber;

	@Column(length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private SourceChannel sourceChannel;

	@Column(nullable = false, columnDefinition = "text")
	private String rawText;

	@Column(length = 500)
	private String locationText;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ComplaintStatus status;

	protected Complaint() {
	}

	public Complaint(SourceChannel sourceChannel, String rawText, String locationText) {
		this.sourceChannel = sourceChannel;
		this.rawText = rawText;
		this.locationText = locationText;
		this.status = ComplaintStatus.RECEIVED;
		this.title = buildTitle(rawText);
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (status == null) {
			status = ComplaintStatus.RECEIVED;
		}
		if (receiptNumber == null) {
			String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
			receiptNumber = "CIV-" + date + "-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
		}
	}

	public UUID getId() {
		return id;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	public String getTitle() {
		return title;
	}

	public SourceChannel getSourceChannel() {
		return sourceChannel;
	}

	public String getRawText() {
		return rawText;
	}

	public String getLocationText() {
		return locationText;
	}

	public ComplaintStatus getStatus() {
		return status;
	}

	public void markAnalyzed() {
		if (status == ComplaintStatus.RECEIVED) {
			status = ComplaintStatus.ANALYZED;
		}
	}

	public void markDraftGenerated() {
		if (status == ComplaintStatus.RECEIVED || status == ComplaintStatus.ANALYZED) {
			status = ComplaintStatus.DRAFT_GENERATED;
		}
	}

	public void changeStatus(ComplaintStatus status) {
		this.status = status;
	}

	private String buildTitle(String rawText) {
		if (rawText == null || rawText.isBlank()) {
			return null;
		}
		String normalized = rawText.replaceAll("\\s+", " ").trim();
		return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
	}
}
