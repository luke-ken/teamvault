package io.github.lukeken.teamvault.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(@NotBlank @Size(max = 100) String name, @NotNull Tier tier) {
}
