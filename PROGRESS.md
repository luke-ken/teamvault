# TeamVault progress

Newest on top. Format: `YYYY-MM-DD – sprint/session – done / next`.

- 2026-10-25 – S2·Session 4 complete, Part 1 done – OpenAPI end to end. ADR-006 (code-first
  with springdoc 3.1.1, document exported by a test and committed, UI in dev only; accepted).
  `GET /api/me` in a new `UserController` (first endpoint built with the document in view).
  `OpenApiConfig`: info, fixed server, bearer scheme as a global requirement cleared on the two
  public controllers with an empty `@SecurityRequirements`, and a customizer that declares the
  `ProblemDetail` schema with `requestId` and `errors` plus a `default` problem response on every
  operation and a `401` on the secured ones (springdoc adds nothing for the advice on its own).
  First look at the generated document found four coarse spots, all fixed in code, no `@Schema`:
  colliding operation ids (`get_1`, `list_1`) by renaming the controller methods, class-name tags
  by one `@Tag` per controller, `*/*` success bodies by `default-produces-media-type`, and the
  upload documented as JSON by `consumes = multipart/form-data` (download keeps `*/*` on purpose).
  `application-dev.yaml` is the first profile in main; run command gained `-Dspring-boot.run.profiles=dev`.
  Tests: `OpenApiDocumentTest` (public, bearer, `/api/**` only, 401 on secured and not on public,
  schema members, UI 404 under `test`, export of `docs/openapi.yaml`; two runs give one file thanks
  to `writer-with-order-by-keys`) and `SwaggerUiEnabledTest` (own context, the switch in the on
  position). `backend/http/teamvault.http`: the README flow plus nine error cases. 38/38 green,
  four commits plus docs. README: run command, Swagger note, contract paragraph, `/api/me` row.
  Ladder: two rows to built. **Next:** Session 5 (T) test pyramid and test doubles, then Session 6
  Mockito unit tests for `MembershipGuard`, `AuthService`, `CompanyService`.
- 2026-10-22 – S2·Session 2 complete – Error contract switched. `ApiExceptionHandler` now
  extends `ResponseEntityExceptionHandler`: four overrides where Spring owns the mapping
  (validation with a sorted `errors` list, unreadable body, type mismatch, upload size), one
  handler for every `ApiException` via its type, three for authentication (no token, bad
  token, bad credentials), two for infrastructure, one about:blank catch-all; every body
  passes `handleExceptionInternal` once for the `requestId`. `JsonAuthenticationEntryPoint`
  no longer serialises anything, it hands the exception to the MVC resolver (Boot's
  ObjectMapper lacks the ProblemDetail mixin, the converter has it). Six assertions migrated
  to `$.detail` or `$.errors`, the login same-body test compares bodies without the id.
  New: `ErrorContractApiIntegrationTest` (both exits, about:blank 404, request id generated,
  echoed, replaced) and `ApiExceptionHandlerTest`, the second slice test. Two Spring 7 facts
  learnt the hard way: a bare `ProblemDetail` leaves `type` out when it is about:blank, and
  Boot's test exclude filter hides classes nested in a test class from scanning, so the
  throwaway controller needs `@Import`. ADR-005 accepted. 30/30 green, two commits plus
  docs. README error paragraph and example bodies, two ladder rows to built. **Next:**
  Session 3 (T) OpenAPI concepts, then Session 4 springdoc gated by profile and `GET /api/me`.
- 2026-10-21 – S2·Session 1 and 2 (first half) – Error contract started. ADR-005 (Problem
  Details as the error body, `type` as a GitHub anchor into the new `docs/errors.md`, the
  security entry point delegating to the MVC resolver so there is one writer of error bodies,
  request id by filter ahead of Spring Security; status proposed until the switch lands),
  build plan in `docs/plans/error-contract-plan.md`. Code: `ProblemType` enum (slug + status,
  413 on the un-deprecated `CONTENT_TOO_LARGE`), `ApiException` base carrying its type, the
  three exceptions as subclasses, the guard's two 403s named, `RequestIdFilter` (validated
  `X-Request-Id` or UUID, MDC, header on every response), `logging.pattern.correlation`
  verified in the test log. 18/18 green, two commits. **Next:** plan steps 3 to 5, the advice
  on `ResponseEntityExceptionHandler`, the delegating entry point, test migration, ADR-005
  to accepted; then the error-contract tests (steps 6, 7) and README.
- 2026-10-21 – S1·S6 + S10 to S12, Sprint 1 build complete – Three commits. Shared
  `AbstractIntegrationTest` (one configuration, one Spring context and one Postgres container
  for every API test; storage dir created by hand because a `@TempDir` dies with its class
  while the cached context lives on), the two existing tests shrunk to their cases, the
  `contextLoads` class deleted as redundant. `MembershipGuard` holds the ADR-003 rule in one
  place (`requireMember`, `requireAdmin` on top), `FileService` uses it, `Caller.id(jwt)`
  shared by the controllers. Companies: create with the caller as ADMIN in the same
  transaction, get for members, "my companies" with the caller's role in each
  (`@EntityGraph` so it is one query). Members: add as ADMIN only (404 unknown email, 409
  already a member, unique constraint as the race backstop), list for members. Rule kept from
  files: a non-member gets 403 whether the company exists or not. New 400 for unreadable
  bodies (unknown enum value was a 500). Tests: company (4) and membership (3) classes; the
  membership test registers its own users because the database is shared across classes and
  the file test needs bob outside Acme. 18/18 green, formatter clean. README: full bearer
  flow register, login, company, member, upload, list, 403 for the role; six new API rows;
  role-model paragraph. **Next:** ten minutes aloud on auth (DoD line 6), then Sprint 2
  breakdown: OpenAPI gated by profile, Problem Details, request id in error and log.
- 2026-10-20 – S1·S10 + readiness – Six commits. Registration (`AuthService.register`: exists
  check for the readable 409, `uq_app_user_email` as the race backstop, now mapped from
  `DataIntegrityViolationException` to 409 in the advice; email stored lower-cased and looked
  up lower-cased, so the unique constraint is effectively case-insensitive), `RegisterRequest`
  (password 8 to 72, the bcrypt input limit), `UserResponse` without the hash, login moved
  from the controller into the service. Plan step 10 done: `AuthApiIntegrationTest`, six
  cases (register then login with the token decoded by the production decoder, duplicate
  email 409 across letter case, short password 400 naming the field, wrong password and
  unknown email byte-identical 401, garbage bearer 401 as JSON with the challenge header,
  health public and detail-free). Actuator health with liveness and readiness probes,
  `show-details: never`, exposure health only, verified live (`/actuator/env` is 404).
  Dependabot for Maven, weekly, grouped; the github-actions entry failed its first run with
  "no workflow files" and was removed until CI exists. Formatter chore: Spring Java Format
  at the validate phase, repo-wide apply in one commit, not Spotless (google-java-format
  lineage does not run on JDK 26). New `docs/production-readiness.md`: every production
  item as built, sprint N or named, with the one-line reason; principle: multi-instance safe
  by construction now, infrastructure where a sprint pays for it, Kubernetes named only.
  12/12 green. **Next:** company create with caller as ADMIN, member add as the first role
  check, then README flow and Sprint 1 close. Shared integration base class when the third
  integration test arrives (each test class boots its own context today).
- 2026-09-29 (2) – S1·S9–S10 (part) – Step 19: README on the JWT state. Status line, design
  decision 3 (token = identity only, authorization stays the per-request membership lookup),
  stack line, a "configure the signing secret" step (fail-fast boot, `.env.example`, openssl),
  the curl walkthrough on bearer tokens incl. the 401 without a token and the same-body,
  same-timing login failure, API table with an auth column, 401 filter-chain vs controller
  error paths, "deliberately not built" now names refresh tokens and revocation with the
  ADR-004 revisit triggers. Plan status updated. **Next:** step 10 (`AuthApiIntegrationTest`),
  Spotless chore.
- 2026-09-29 – S1·S9–S10 (part) – The swap landed, steps 12 to 18 in one feature commit,
  ADR-004 accepted. `SecurityConfig`: `httpBasic` out, `oauth2ResourceServer(jwt)` in,
  `/api/auth/**` public, `JsonAuthenticationEntryPoint` wired for both 401 paths (no token
  via `exceptionHandling`, bad token via the resource server). `FileController` takes
  `@AuthenticationPrincipal Jwt`, caller id from `sub`; `FileService` checks membership
  by id directly, `AppUserRepository` dependency and one query per request gone. Step 13
  was already done (pulled into step 5). Tests: bearer helper does a real login per user
  (cached static), assertions byte-identical; ping slice imports the entry point and mocks
  `JwtDecoder`. 6/6 green. Lesson: `@WebMvcTest` does not scan `@Component`, so a new
  dependency in `SecurityConfig` drags its bean into every slice that imports it.
  **Next:** step 19 (README: intro, bearer curl flow, API table, JWT out of "not built"),
  step 10 (`AuthApiIntegrationTest`), then Spotless as a chore commit.
- 2026-09-21 – S1·S9–S10 (part) – JWT steps 7 to 9 built in duo, plus step 16 pulled
  forward: `LoginRequest` (validated, password never in `toString`), `AuthenticationConfig`
  (`ProviderManager` over `DaoAuthenticationProvider`, kept out of `SecurityConfig` so the
  ping slice test keeps importing the chain without a `UserDetailsService`), `AuthController`
  `POST /api/auth/login`, `JsonAuthenticationEntryPoint` (401 as `ApiError` JSON plus
  `WWW-Authenticate: Bearer`, Jackson 3 mapper), advice handlers for `AuthenticationException`
  (one message for unknown email and wrong password) and `@Valid` body failures (400 instead
  of the catch-all 500). Review caught one bug before commit: `joining("")` instead of
  `joining(", ")`. 6/6 green; login not reachable yet, HTTP Basic still in front. Decided:
  Spotless + palantir-java-format as a chore commit after the feature. **Next:** step 12,
  the swap (`httpBasic` out, `oauth2ResourceServer(jwt)` in, `/api/auth/**` permitted, entry
  point wired), then 13 to 18 and the login integration test behind it.
- 2026-09-20 – S1·S9–S10 (part) – ADR-004 finalised and committed (self-issued JWT via
  Spring resource-server support, HS256, identity-only claims, 15-min TTL, no refresh
  token; revisit triggers named). `docs/plans/jwt-swap-plan.md`: 20-step build order,
  bottom-up in dependency order, written before the first line of code. Steps 1–6 built
  in duo (user types, assistant reviews) and green: resource-server starter, validated
  `JwtProperties` with fail-fast boot on a missing/short secret (verified: boot dies with
  the env-var name in the message), `.env` import + `.env.example`, test profile with a
  fixed secret, `JwtConfig` (encoder/decoder beans, HS256 pinned, issuer validated),
  `AuthenticatedUser` record principal carrying the UUID, `TokenService`. 6/6 tests
  still green; HTTP Basic still active. Three commits. **Next:** steps 7–9 (login
  endpoint + `AuthenticationManager` bean + JSON 401 entry point), then the swap
  (steps 12–18), README, ADR status to accepted.
- 2026-08-17 – S1·S3–S8 – The big push. ADR-003 (tenant isolation: shared schema +
  company_id, service-layer enforcement, RLS named as hardening path), ERD hand-sketched
  then captured in `docs/erd.md` with per-index justifications, `V1__init.sql` (4 tables,
  tenant-scoped uniques, company_id-first composite indexes) + `V2__seed.sql` (two
  disjoint tenants, deterministic UUIDs). JPA entities + repositories (self-written,
  validated against the schema via ddl-auto=validate). Interim auth: HTTP Basic over
  bcrypt `app_user` rows, deny-by-default kept per ADR-002, JWT stays the named swap.
  File feature: multipart upload → local-FS blob store (single swap-point class) +
  metadata row, tenant-scoped list/download, clean JSON errors without stack traces.
  `FileApiIntegrationTest`: round-trip, duplicate-filename 409, 401, and the
  cross-tenant negative test (403 as non-member, 404 for a foreign file id under your
  own company). 6/6 green against real Postgres (Testcontainers); live curl round-trip
  verified. README rewritten: design-in-three-decisions, runnable curl examples,
  "deliberately not built" list. **Next:** company/user CRUD + registration, JWT swap,
  strip the scaffold TODO comments from entities/repositories.
- 2026-07-05 – S1·S2 – First green run. Spring Boot 4.1/Java 21 skeleton in `backend/`
  (Web, JPA, Flyway, Security, Validation, Actuator, Testcontainers). `GET /api/ping` → pong,
  everything else deny-by-default (ADR-002). No datasource in yaml: docker-compose starts
  dev Postgres, Testcontainers covers tests (ADR-001). `./mvnw test` green (2/2), live run
  verified end-to-end. Gotcha: Boot 4 moved `@WebMvcTest` to `o.s.boot.webmvc.test.autoconfigure`.
  **Next:** Session 3 (T), relational modeling theory; then Session 4, the ERD.
- 2026-07-04 – setup – Repo is live: git init + first push to `luke-ken/teamvault` (public).
  README stub with pitch/stack/status, ADR template, .gitignore. Building in public from here.
  **Next:** Sprint 1 · Session 2: Spring Initializr, Postgres in Docker, `/ping` green run.
- 2026-06-07 – setup – Project folder + CLAUDE.md created. Not yet a git repo / no code.
