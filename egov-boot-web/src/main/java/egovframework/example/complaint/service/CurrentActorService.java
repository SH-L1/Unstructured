package egovframework.example.complaint.service;

import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentActorService {

	public Actor current(String fallbackRole) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
			String role = authentication.getAuthorities().stream()
					.map(GrantedAuthority::getAuthority)
					.filter(authority -> authority.startsWith("ROLE_"))
					.map(authority -> authority.substring(5))
					.findFirst()
					.orElse(fallbackRole);
			return new Actor(authentication.getName(), role);
		}
		String normalizedRole = fallbackRole.toLowerCase(Locale.ROOT);
		return new Actor("system-" + normalizedRole, fallbackRole);
	}

	public record Actor(String name, String role) {
	}
}
