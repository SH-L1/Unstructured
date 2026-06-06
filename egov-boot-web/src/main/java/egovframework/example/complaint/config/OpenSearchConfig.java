package egovframework.example.complaint.config;

import java.time.Duration;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

@Configuration
public class OpenSearchConfig {

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(name = "app.rag.opensearch.enabled", havingValue = "true")
	SdkHttpClient openSearchSdkHttpClient() {
		return ApacheHttpClient.builder()
				.connectionTimeout(Duration.ofSeconds(10))
				.socketTimeout(Duration.ofSeconds(30))
				.build();
	}

	@Bean
	@ConditionalOnProperty(name = "app.rag.opensearch.enabled", havingValue = "true")
	OpenSearchClient openSearchClient(
			SdkHttpClient openSearchSdkHttpClient,
			@Value("${app.rag.opensearch.endpoint}") String endpoint,
			@Value("${app.rag.opensearch.region}") String region
	) {
		if (!StringUtils.hasText(endpoint)) {
			throw new IllegalStateException("OpenSearch endpoint is required when app.rag.opensearch.enabled=true");
		}
		return new OpenSearchClient(new AwsSdk2Transport(
				openSearchSdkHttpClient,
				normalizeEndpoint(endpoint),
				"aoss",
				Region.of(region),
				AwsSdk2TransportOptions.builder().build()
		));
	}

	private String normalizeEndpoint(String endpoint) {
		String normalized = endpoint.trim()
				.replace("https://", "")
				.replace("http://", "");
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}
}
