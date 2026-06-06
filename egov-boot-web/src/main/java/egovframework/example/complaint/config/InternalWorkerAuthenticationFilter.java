package egovframework.example.complaint.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalWorkerAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIX = "/internal/v1/worker/";
	private final String serviceToken;

	public InternalWorkerAuthenticationFilter(@Value("${app.worker.service-token:}") String serviceToken) {
		this.serviceToken = serviceToken;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		String supplied = authorization != null && authorization.startsWith("Bearer ")
				? authorization.substring("Bearer ".length())
				: "";
		if (serviceToken == null || serviceToken.length() < 32
				|| !MessageDigest.isEqual(
						serviceToken.getBytes(StandardCharsets.UTF_8),
						supplied.getBytes(StandardCharsets.UTF_8)
				)) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Valid internal worker credentials are required");
			return;
		}
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"internal-python-worker",
				null,
				List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_WORKER"))
		));
		filterChain.doFilter(request, response);
	}
}
