package egovframework.example.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EgovConfigDatasource {

	@Bean
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties dataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean(name = "dataSource")
	public DataSource dataSource(DataSourceProperties dataSourceProperties) {
		return dataSourceProperties.initializeDataSourceBuilder().build();
	}

}
