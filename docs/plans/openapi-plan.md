# Feature plan: OpenAPI document, Swagger UI in dev only, GET /api/me

Code-first OpenAPI with springdoc 3.x, the document committed as `docs/openapi.yaml`, Swagger UI
reachable only under the `dev` profile. Decision and trade-offs in
`../adr/006-api-documentation-code-first-openapi.md`; this file is the build order. Method as in
`error-contract-plan.md`: inventory, forks settled before the first line, bottom-up, tests with
each step.

## Inventory

- Controllers: `PingController` (public), `AuthController` (public, `/api/auth/**`),
  `CompanyController`, `MembershipController`, `FileController` (bearer). Actuator health is
  public but not part of the API.
- Request and response records carry the validation annotations springdoc reads (`@NotBlank`,
  `@Size`, `@Email`, `@NotNull`). `FileController` uploads multipart.
- Error bodies: `ProblemDetail` with the extension members `requestId` and, on validation,
  `errors`. Spring's class has no fields for them, so reflection cannot document them.
- Profiles today: none in `main`, `test` in the test sources. `./mvnw spring-boot:run` runs the
  default profile. Dependencies: springdoc 3.1.1 is the Boot 4 line (checked 2026-10-23).
- Missing: an endpoint the frontend can call first to learn who is logged in.

## Design forks, settled

1. **Code-first, document committed.** springdoc derives the document from the controllers; an
   export test writes `/v3/api-docs.yaml` to `docs/openapi.yaml`. The committed file is the
   deliverable and an API change shows up as a diff in review. No annotation carpet: `@Operation`
   or `@Schema` only where the derived text is wrong or missing.
2. **Contract public, UI a dev tool.** `/v3/api-docs/**` is `permitAll` in every profile.
   `springdoc.swagger-ui.enabled` is `false` in `application.yaml` and `true` in a new
   `application-dev.yaml`; the README run command becomes
   `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. The UI paths are `permitAll` too, so
   a disabled UI answers 404 (no static resource, through the advice) and not 401. Tests run under
   `test`, where the UI is off: one test asserts the 404; a second, small test class switches the
   property on and asserts the UI answers, at the cost of one extra context.
3. **Bearer scheme once, globally.** One `OpenAPI` bean (`config/OpenApiConfig`): info, a fixed
   `servers` entry (otherwise the export carries MockMvc's `http://localhost`), the `bearer`
   security scheme (http, bearer, JWT) and a global security requirement. The two public
   controllers carry a class-level empty `@SecurityRequirements`, which clears the lock for their
   operations. `springdoc.paths-to-match: /api/**` keeps Boot's error controller out.
4. **Error responses declared, not inferred.** An `OpenApiCustomizer` in the same config adds the
   `ProblemDetail` schema by hand (type, title, status, detail, instance, requestId, errors as a
   list of {field, message}) and gives every operation a `default` response with
   `application/problem+json`, plus `401` on secured operations. Specific 403/404/409 stay
   undeclared unless a client needs them; `detail` is not contract. springdoc's own responses
   derived from the advice (`override-with-generic-response`) are turned off if the first run
   shows them as noise. Open until the first run: what springdoc 3 does with Spring's
   `ProblemDetail` type on its own.
5. **Stable export.** `springdoc.writer-with-order-by-keys: true` so two runs produce the same
   file and the diff shows only real changes. YAML, not JSON: smaller diffs.
6. **`GET /api/me`.** `user/UserController` with `user/UserService.me(UUID)`: the caller's row by
   the token subject, as `UserResponse` ({id, email, displayName}). A valid token whose user is
   gone is a 403 "Caller no longer exists", the precedent from `CompanyService`. Outside
   `/api/auth/**` on purpose: it is a protected resource, not part of login.
7. **ADR numbering.** This decision is ADR-006; the refresh-token stretch moves to ADR-007 in the
   sprint breakdown.

## Build order

### Endpoint (independent of springdoc)
1. `user/UserService`, `user/UserController`. Test in `AuthApiIntegrationTest`: `me_returnsTheCallerFromTheToken`
   (id equals the token subject, email alice, displayName present).
   Commit: `feat: GET /api/me returns the caller`.

### Document
2. Dependency `springdoc-openapi-starter-webmvc-ui` 3.1.1. `config/OpenApiConfig` (bean + customizer).
   `application.yaml`: `springdoc.swagger-ui.enabled: false`, `paths-to-match`, `writer-with-order-by-keys`.
   New `application-dev.yaml` with the UI on. `SecurityConfig`: `permitAll` for `/v3/api-docs/**`,
   `/swagger-ui/**`, `/swagger-ui.html`. `@SecurityRequirements` on `PingController` and `AuthController`.
   First run with the `dev` profile: read `/v3/api-docs`, settle fork 4's open point, adjust.
3. `common/OpenApiDocumentTest` (shared context): `/v3/api-docs` answers 200 without a token; the
   document has the bearer scheme, every path starts with `/api/`, `/api/me` is present, the
   `ProblemDetail` schema has `requestId`; `/swagger-ui.html` answers 404 under `test`. Then the
   export: fetch `/v3/api-docs.yaml`, write `../docs/openapi.yaml`.
   `common/SwaggerUiEnabledTest` (own context, property on): `/swagger-ui.html` redirects or answers 200.
   Commit: `feat: OpenAPI document with bearer scheme, Swagger UI in dev only` (includes `docs/openapi.yaml`).

### Client file
4. `backend/http/teamvault.http`: the README flow as requests with variables (login stores the token
   with a response handler), both tenants, the three error cases. Commit: `chore: HTTP client file for the README flow`.

### Docs
5. README: run command with the `dev` profile, Swagger UI URL, API section links `docs/openapi.yaml`.
   Ladder rows "Swagger UI off outside dev" and "OpenAPI document generated" to built. ADR-006 accepted.
   PROGRESS, sprint breakdown (Session 4 boxes, ADR-007 for refresh tokens).
