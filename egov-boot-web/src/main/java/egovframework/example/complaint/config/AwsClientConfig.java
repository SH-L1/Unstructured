package egovframework.example.complaint.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsClientConfig {

	@Bean
	@ConditionalOnProperty(name = "app.aws.s3.enabled", havingValue = "true")
	S3Client s3Client(@Value("${app.aws.region:ap-northeast-2}") String region) {
		return S3Client.builder()
				.region(Region.of(region))
				.build();
	}

	@Bean
	@ConditionalOnProperty(name = "app.aws.bedrock.enabled", havingValue = "true")
	BedrockRuntimeClient bedrockRuntimeClient(@Value("${app.aws.bedrock.region:us-east-1}") String region) {
		return BedrockRuntimeClient.builder()
				.region(Region.of(region))
				.build();
	}
}
