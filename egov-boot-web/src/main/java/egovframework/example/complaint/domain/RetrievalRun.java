package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "retrieval_runs")
public class RetrievalRun extends BaseTimeEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false)
	private UUID complaintId;

	private UUID processingJobId;

	@Column(nullable = false, columnDefinition = "text")
	private String queryText;

	@Column(nullable = false, length = 40)
	private String purpose;

	@Column(nullable = false, length = 40)
	private String status;

	@Column(nullable = false, columnDefinition = "text")
	private String filtersJson;

	protected RetrievalRun() {
	}

	public RetrievalRun(UUID complaintId, UUID processingJobId, String queryText, String purpose, String filtersJson) {
		this.complaintId = complaintId;
		this.processingJobId = processingJobId;
		this.queryText = queryText;
		this.purpose = purpose;
		this.filtersJson = filtersJson;
		this.status = "SUCCEEDED";
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	public UUID getId() {
		return id;
	}
}
