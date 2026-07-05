package io.github.lukeken.teamvault.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ping")
class PingController {

	@GetMapping
	PingResponse ping() {
		return new PingResponse("pong");
	}

	record PingResponse(String message) {
	}

}