package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.ApiUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiUserRepository extends JpaRepository<ApiUser, Long> {

	Optional<ApiUser> findByApiKeyHashAndActiveTrue(String apiKeyHash);
}
