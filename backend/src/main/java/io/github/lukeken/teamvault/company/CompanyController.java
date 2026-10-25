package io.github.lukeken.teamvault.company;

import io.github.lukeken.teamvault.auth.Caller;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/companies")
@Tag(name = "companies")
public class CompanyController {

	private final CompanyService companyService;

	public CompanyController(CompanyService companyService) {
		this.companyService = companyService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CompanyResponse createCompany(@AuthenticationPrincipal Jwt caller,
			@Valid @RequestBody CreateCompanyRequest request) {
		return companyService.create(Caller.id(caller), request);
	}

	@GetMapping
	public List<CompanyResponse> listMyCompanies(@AuthenticationPrincipal Jwt caller) {
		return companyService.mine(Caller.id(caller));
	}

	@GetMapping("/{companyId}")
	public CompanyResponse getCompany(@AuthenticationPrincipal Jwt caller, @PathVariable UUID companyId) {
		return companyService.get(Caller.id(caller), companyId);
	}

}
