package egovframework.example.complaint.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ObjectProvider<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilterProvider,
			ObjectProvider<ApiAuditLogFilter> apiAuditLogFilterProvider
	) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/**", "/actuator/health", "/actuator/info").permitAll()
						.anyRequest().permitAll()
				);
		ApiAuditLogFilter apiAuditLogFilter = apiAuditLogFilterProvider.getIfAvailable();
		if (apiAuditLogFilter != null) {
			http.addFilterBefore(apiAuditLogFilter, UsernamePasswordAuthenticationFilter.class);
		}
		ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = apiKeyAuthenticationFilterProvider.getIfAvailable();
		if (apiKeyAuthenticationFilter != null && apiAuditLogFilter != null) {
			http.addFilterAfter(apiKeyAuthenticationFilter, ApiAuditLogFilter.class);
		}
		else if (apiKeyAuthenticationFilter != null) {
			http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		}
		return http.build();
	}
}
