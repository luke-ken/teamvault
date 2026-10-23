package io.github.lukeken.teamvault.user;

import io.github.lukeken.teamvault.auth.Caller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public UserResponse me(@AuthenticationPrincipal Jwt caller) {
		return userService.me(Caller.id(caller));
	}

}
