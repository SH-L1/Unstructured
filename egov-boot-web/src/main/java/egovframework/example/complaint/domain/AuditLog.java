package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String method;

	@Column(nullable = false, length = 500)
	private String path;

	@Column(length = 100)
	private String actor;

	@Column(length = 100)
	private String clientIp;

	@Column(nullable = false)
	private int statusCode;

	@Column(nullable = false)
	private long durationMs;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected AuditLog() {
	}

	public AuditLog(String method, String path, String actor, String clientIp, int statusCode, long durationMs) {
		this.method = method;
		this.path = path;
		this.actor = actor;
		this.clientIp = clientIp;
		this.statusCode = statusCode;
		this.durationMs = durationMs;
		this.createdAt = LocalDateTime.now();
	}
}
