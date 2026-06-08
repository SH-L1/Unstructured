package egovframework.example.complaint.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ObjectProvider<ApiAuditLogFilter> apiAuditLogFilterProvider,
			InternalWorkerAuthenticationFilter internalWorkerAuthenticationFilter,
			@Value("${app.security.session.enabled:false}") boolean sessionEnabled
	) throws Exception {
		if (sessionEnabled) {
			http
					.csrf(csrf -> csrf
							.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
							.ignoringRequestMatchers("/internal/v1/worker/**")
					)
					.authorizeHttpRequests(auth -> auth
							.requestMatchers("/login", "/error").permitAll()
							.requestMatchers("/internal/v1/worker/**").hasRole("INTERNAL_WORKER")
							.requestMatchers("/actuator/**").hasAnyRole("ADMIN", "AUDITOR")
							.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasAnyRole("ADMIN", "KNOWLEDGE_ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/complaints").hasAnyRole("INTAKE", "ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/complaints/*/analysis-runs").hasAnyRole("INTAKE", "REVIEWER", "ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/complaints/*/draft-runs").hasAnyRole("REVIEWER", "ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/complaints/*/attachments").hasAnyRole("INTAKE", "REVIEWER", "ADMIN")
							.requestMatchers(HttpMethod.DELETE, "/api/v1/complaints/*/attachments/*").hasAnyRole("INTAKE", "REVIEWER", "ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/issues/*/location-confirmations").hasAnyRole("REVIEWER", "ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/issues/*/department-confirmations").hasAnyRole("REVIEWER", "ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/drafts/*/reviews").hasAnyRole("REVIEWER", "ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/drafts/*/approvals").hasAnyRole("APPROVER", "ADMIN")
							.requestMatchers(HttpMethod.POST, "/api/v1/complaints/*/complete").hasAnyRole("APPROVER", "ADMIN")
							.requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(
									"INTAKE", "REVIEWER", "APPROVER", "KNOWLEDGE_ADMIN", "AUDITOR", "ADMIN"
							)
							.anyRequest().authenticated()
					)
					.formLogin(Customizer.withDefaults());
		}
		else {
			http.csrf(csrf -> csrf.disable())
					.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		}
		ApiAuditLogFilter apiAuditLogFilter = apiAuditLogFilterProvider.getIfAvailable();
		http.addFilterBefore(internalWorkerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		if (apiAuditLogFilter != null) {
			http.addFilterBefore(apiAuditLogFilter, UsernamePasswordAuthenticationFilter.class);
		}
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	@ConditionalOnProperty(name = "app.security.session.enabled", havingValue = "true")
	UserDetailsService userDetailsService(
			PasswordEncoder encoder,
			@Value("${app.security.session.intake-password:change-me-intake}") String intakePassword,
			@Value("${app.security.session.reviewer-password:change-me-reviewer}") String reviewerPassword,
			@Value("${app.security.session.approver-password:change-me-approver}") String approverPassword,
			@Value("${app.security.session.knowledge-admin-password:change-me-knowledge-admin}") String knowledgeAdminPassword,
			@Value("${app.security.session.auditor-password:change-me-auditor}") String auditorPassword,
			@Value("${app.security.session.admin-password:change-me-admin}") String adminPassword
	) {
		requireConfiguredPassword("INTAKE", intakePassword);
		requireConfiguredPassword("REVIEWER", reviewerPassword);
		requireConfiguredPassword("APPROVER", approverPassword);
		requireConfiguredPassword("KNOWLEDGE_ADMIN", knowledgeAdminPassword);
		requireConfiguredPassword("AUDITOR", auditorPassword);
		requireConfiguredPassword("ADMIN", adminPassword);
		return new InMemoryUserDetailsManager(
				User.withUsername("intake").password(encoder.encode(intakePassword)).roles("INTAKE").build(),
				User.withUsername("reviewer").password(encoder.encode(reviewerPassword)).roles("REVIEWER").build(),
				User.withUsername("approver").password(encoder.encode(approverPassword)).roles("APPROVER").build(),
				User.withUsername("knowledge-admin").password(encoder.encode(knowledgeAdminPassword)).roles("KNOWLEDGE_ADMIN").build(),
				User.withUsername("auditor").password(encoder.encode(auditorPassword)).roles("AUDITOR").build(),
				User.withUsername("admin").password(encoder.encode(adminPassword)).roles("ADMIN").build()
		);
	}

	private void requireConfiguredPassword(String role, String password) {
		if (password == null || password.isBlank() || password.startsWith("change-me")) {
			throw new IllegalStateException(role + " session password must be explicitly configured");
		}
	}
}
