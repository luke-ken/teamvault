package io.github.lukeken.teamvault.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The OpenAPI document (ADR-006). Code-first: springdoc reads the controllers and
 * records; this class adds what reflection cannot see: the bearer scheme, a fixed server,
 * and the Problem Details error shape with its extension members (ADR-005).
 */
@Configuration
public class OpenApiConfig {

	static final String BEARER = "bearer";
	static final String PROBLEM = "ProblemDetail";

	static final String PROBLEM_JSON = "application/problem+json";

	@Bean
	OpenAPI teamvaultOpenApi() {
		return new OpenAPI()
			.info(new Info().title("TeamVault API")
				.version("0.1.0")
				.description("Multi-tenant file vault. Errors follow RFC 9457, see docs/errors.md."))
			// Fixed on purpose: otherwise the exported document carries the host of
			// whoever
			// generated it (MockMvc says http://localhost).
			.servers(List.of(new Server().url("http://localhost:8080").description("local dev")))
			.components(new Components().addSecuritySchemes(BEARER,
					new SecurityScheme().type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("Access token from POST /api/auth/login")))
			// Global: every operation needs the token unless it opts out with an empty
			// @SecurityRequirements (ping, auth).
			.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

	// Error responses are produced by the advice, not by the controllers, so springdoc
	// cannot infer them. Declared once here: one schema, a default error on every
	// operation,
	// 401 on the secured ones.
	@Bean
	OpenApiCustomizer problemDetailResponses() {
		return openApi -> {
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			openApi.getComponents().addSchemas(PROBLEM, problemSchema());
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
				operation.getResponses().addApiResponse("default", problemResponse("Error, see docs/errors.md"));
				if (secured(operation)) {
					operation.getResponses()
						.addApiResponse("401", problemResponse("Missing or invalid token, WWW-Authenticate: Bearer"));
				}
			}));
		};
	}

	// null means "inherits the global requirement"; an empty list is the explicit
	// opt-out.
	private static boolean secured(Operation operation) {
		return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
	}

	private static Schema<?> problemSchema() {
		Schema<?> violation = new ObjectSchema().addProperty("field", new StringSchema())
			.addProperty("message", new StringSchema());
		return new ObjectSchema()
			.addProperty("type",
					new StringSchema().format("uri")
						.description("Anchor into docs/errors.md; absent means about:blank"))
			.addProperty("title", new StringSchema())
			.addProperty("status", new IntegerSchema())
			.addProperty("detail", new StringSchema().description("For humans, may change"))
			.addProperty("instance", new StringSchema().description("Request path"))
			.addProperty("requestId", new StringSchema().description("Same value as the X-Request-Id header"))
			.addProperty("errors", new ArraySchema().items(violation).description("validation-failed only"));
	}

	private static ApiResponse problemResponse(String description) {
		return new ApiResponse().description(description)
			.content(new Content().addMediaType(PROBLEM_JSON,
					new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
	}

}