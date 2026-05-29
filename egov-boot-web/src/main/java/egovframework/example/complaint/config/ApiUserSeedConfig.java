package egovframework.example.complaint.config;

import egovframework.example.complaint.domain.ApiUser;
import egovframework.example.complaint.domain.ApiUserRole;
import egovframework.example.complaint.repository.ApiUserRepository;
import egovframework.example.complaint.service.ApiKeyHashService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.security.api-key.enabled", havingValue = "true")
public class ApiUserSeedConfig implements ApplicationRunner {

	private final ApiUserRepository apiUserRepository;
	private final ApiKeyHashService apiKeyHashService;
	private final String username;
	private final String apiKey;

	public ApiUserSeedConfig(
			ApiUserRepository apiUserRepository,
			ApiKeyHashService apiKeyHashService,
			@Value("${app.security.bootstrap-admin.username:local-admin}") String username,
			@Value("${app.security.api-key.value:}") String apiKey
	) {
		this.apiUserRepository = apiUserRepository;
		this.apiKeyHashService = apiKeyHashService;
		this.username = username;
		this.apiKey = apiKey;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(apiKey)) {
			return;
		}
		String apiKeyHash = apiKeyHashService.sha256(apiKey);
		if (apiUserRepository.findByApiKeyHashAndActiveTrue(apiKeyHash).isEmpty()) {
			apiUserRepository.save(new ApiUser(username, apiKeyHash, ApiUserRole.ADMIN));
		}
	}
}
