package egovframework.example.complaint.config;

import egovframework.example.complaint.domain.AuditLog;
import egovframework.example.complaint.repository.AuditLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name = "app.audit.enabled", havingValue = "true", matchIfMissing = true)
public class ApiAuditLogFilter extends OncePerRequestFilter {

	private final AuditLogRepository auditLogRepository;

	public ApiAuditLogFilter(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/")
				|| "GET".equalsIgnoreCase(request.getMethod())
				|| "HEAD".equalsIgnoreCase(request.getMethod())
				|| "OPTIONS".equalsIgnoreCase(request.getMethod());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long startedAt = System.currentTimeMillis();
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			auditLogRepository.save(new AuditLog(
					request.getMethod(),
					request.getRequestURI(),
					resolveActor(),
					resolveClientIp(request),
					response.getStatus(),
					System.currentTimeMillis() - startedAt
			));
		}
	}

	private String resolveActor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
			return authentication.getName();
		}
		return "anonymous";
	}

	private String resolveClientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
