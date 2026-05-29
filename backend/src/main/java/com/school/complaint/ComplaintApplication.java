package com.school.complaint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ComplaintApplication {

	public static void main(String[] args) {
		SpringApplication.run(ComplaintApplication.class, args);
	}

}
