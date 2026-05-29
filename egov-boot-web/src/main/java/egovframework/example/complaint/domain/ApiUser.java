package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_users")
public class ApiUser extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String username;

	@Column(nullable = false, length = 128)
	private String apiKeyHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ApiUserRole role;

	@Column(nullable = false)
	private boolean active = true;

	protected ApiUser() {
	}

	public ApiUser(String username, String apiKeyHash, ApiUserRole role) {
		this.username = username;
		this.apiKeyHash = apiKeyHash;
		this.role = role;
	}

	public String getUsername() {
		return username;
	}

	public String getApiKeyHash() {
		return apiKeyHash;
	}

	public ApiUserRole getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}
}
