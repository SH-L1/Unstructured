package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "complaint_analysis")
public class ComplaintAnalysis extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_id", nullable = false, unique = true)
	private Complaint complaint;

	@Column(nullable = false, length = 100)
	private String intent;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private ComplaintType complaintType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Urgency urgency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Sentiment sentiment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@Column(length = 500)
	private String locationText;

	@Column(columnDefinition = "text")
	private String geoJson;

	@Column(nullable = false, columnDefinition = "text")
	private String analysisJson;

	protected ComplaintAnalysis() {
	}

	public ComplaintAnalysis(Complaint complaint, String intent, ComplaintType complaintType, Urgency urgency, Sentiment sentiment,
			Department department, String locationText, String geoJson, String analysisJson) {
		this.complaint = complaint;
		this.intent = intent;
		this.complaintType = complaintType;
		this.urgency = urgency;
		this.sentiment = sentiment;
		this.department = department;
		this.locationText = locationText;
		this.geoJson = geoJson;
		this.analysisJson = analysisJson;
	}

	public Complaint getComplaint() {
		return complaint;
	}

	public String getIntent() {
		return intent;
	}

	public ComplaintType getComplaintType() {
		return complaintType;
	}

	public Urgency getUrgency() {
		return urgency;
	}

	public Sentiment getSentiment() {
		return sentiment;
	}

	public Department getDepartment() {
		return department;
	}

	public String getLocationText() {
		return locationText;
	}

	public String getGeoJson() {
		return geoJson;
	}

	public String getAnalysisJson() {
		return analysisJson;
	}
}
