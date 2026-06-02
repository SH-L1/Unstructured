package egovframework.example.complaint.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI complaintOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Civil Complaint API")
						.version("1.0.0")
						.description("eGovFrame based civil complaint analysis and draft response API"));
	}
}
