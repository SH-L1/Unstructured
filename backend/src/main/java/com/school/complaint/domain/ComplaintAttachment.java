package com.school.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "complaint_attachments")
public class ComplaintAttachment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "complaint_id", nullable = false)
	private Complaint complaint;

	@Column(nullable = false, length = 255)
	private String originalFileName;

	@Column(nullable = false, length = 500)
	private String s3Key;

	@Column(nullable = false, length = 100)
	private String contentType;

	@Column(nullable = false)
	private long fileSize;

	protected ComplaintAttachment() {
	}

	public ComplaintAttachment(Complaint complaint, String originalFileName, String s3Key, String contentType, long fileSize) {
		this.complaint = complaint;
		this.originalFileName = originalFileName;
		this.s3Key = s3Key;
		this.contentType = contentType;
		this.fileSize = fileSize;
	}

	public Long getId() {
		return id;
	}

	public String getOriginalFileName() {
		return originalFileName;
	}

	public String getS3Key() {
		return s3Key;
	}

	public String getContentType() {
		return contentType;
	}

	public long getFileSize() {
		return fileSize;
	}
}
