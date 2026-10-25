package io.github.lukeken.teamvault.ping;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ping")
@SecurityRequirements // public: clears the global bearer requirement for this controller
@Tag(name = "ping")
class PingController {

	@GetMapping
	PingResponse ping() {
		return new PingResponse("pong");
	}

	record PingResponse(String message) {
	}

}