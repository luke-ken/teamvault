package io.github.lukeken.teamvault.membership;

import io.github.lukeken.teamvault.auth.Caller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies/{companyId}/members")
public class MembershipController {

	private final MembershipService membershipService;

	public MembershipController(MembershipService membershipService) {
		this.membershipService = membershipService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MemberResponse add(@AuthenticationPrincipal Jwt caller, @PathVariable UUID companyId,
			@Valid @RequestBody AddMemberRequest request) {
		return membershipService.add(Caller.id(caller), companyId, request);
	}

	@GetMapping
	public List<MemberResponse> list(@AuthenticationPrincipal Jwt caller, @PathVariable UUID companyId) {
		return membershipService.list(Caller.id(caller), companyId);
	}

}
