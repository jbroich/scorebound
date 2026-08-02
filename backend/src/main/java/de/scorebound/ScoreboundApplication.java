package de.scorebound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ScoreboundApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScoreboundApplication.class, args);
	}

}
