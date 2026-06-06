package egovframework.example.complaint.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsClientConfig {

	@Bean
	@ConditionalOnProperty(name = "app.aws.s3.enabled", havingValue = "true")
	S3Client s3Client(@Value("${app.aws.region:ap-northeast-2}") String region) {
		return S3Client.builder()
				.region(Region.of(region))
				.overrideConfiguration(externalCallConfiguration())
				.build();
	}

	private ClientOverrideConfiguration externalCallConfiguration() {
		return ClientOverrideConfiguration.builder()
				.apiCallAttemptTimeout(Duration.ofSeconds(30))
				.apiCallTimeout(Duration.ofSeconds(45))
				.build();
	}
}
