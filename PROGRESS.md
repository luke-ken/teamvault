# TeamVault progress

Newest on top. Format: `YYYY-MM-DD – sprint/session – done / next`.

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
