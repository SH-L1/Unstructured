package egovframework.example.complaint.config;

import egovframework.example.complaint.domain.ApiUser;
import egovframework.example.complaint.repository.ApiUserRepository;
import egovframework.example.complaint.service.ApiKeyHashService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name = "app.security.api-key.enabled", havingValue = "true")
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private static final String API_KEY_HEADER = "X-API-Key";
	static final String ACTOR_ATTRIBUTE = "apiUser";

	private final String apiKey;
	private final ApiUserRepository apiUserRepository;
	private final ApiKeyHashService apiKeyHashService;

	public ApiKeyAuthenticationFilter(
			@Value("${app.security.api-key.value:}") String apiKey,
			ApiUserRepository apiUserRepository,
			ApiKeyHashService apiKeyHashService
	) {
		this.apiKey = apiKey;
		this.apiUserRepository = apiUserRepository;
		this.apiKeyHashService = apiKeyHashService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return "OPTIONS".equalsIgnoreCase(request.getMethod())
				|| path.equals("/actuator/health")
				|| path.equals("/actuator/info");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!StringUtils.hasText(apiKey)) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "API key is not configured");
			return;
		}
		String providedKey = request.getHeader(API_KEY_HEADER);
		ApiUser apiUser = findApiUser(providedKey);
		if (apiUser == null) {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey");
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
			return;
		}
		if (!isAuthorized(request, apiUser)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient API role");
			return;
		}
		request.setAttribute(ACTOR_ATTRIBUTE, apiUser);
		filterChain.doFilter(request, response);
	}

	private ApiUser findApiUser(String providedKey) {
		if (!StringUtils.hasText(providedKey)) {
			return null;
		}
		return apiUserRepository.findByApiKeyHashAndActiveTrue(apiKeyHashService.sha256(providedKey))
				.orElse(null);
	}

	private boolean isAuthorized(HttpServletRequest request, ApiUser apiUser) {
		return switch (apiUser.getRole()) {
			case ADMIN -> true;
			case OFFICER -> isOfficerAllowed(request);
			case VIEWER -> isReadOnly(request);
		};
	}

	private boolean isOfficerAllowed(HttpServletRequest request) {
		String method = request.getMethod();
		String path = request.getRequestURI();
		if (isReadOnly(request)) {
			return true;
		}
		return ("PATCH".equalsIgnoreCase(method) && path.matches("/api/complaints/[^/]+/status"))
				|| ("PUT".equalsIgnoreCase(method) && path.matches("/api/complaints/[^/]+/draft"));
	}

	private boolean isReadOnly(HttpServletRequest request) {
		return List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod().toUpperCase());
	}
}
