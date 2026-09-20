package io.github.lukeken.teamvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TeamvaultBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TeamvaultBackendApplication.class, args);
	}

}
