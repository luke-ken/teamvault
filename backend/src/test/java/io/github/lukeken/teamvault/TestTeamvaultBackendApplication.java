package io.github.lukeken.teamvault;

import org.springframework.boot.SpringApplication;

public class TestTeamvaultBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(TeamvaultBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
