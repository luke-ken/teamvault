# TeamVault progress

Newest on top. Format: `YYYY-MM-DD – sprint/session – done / next`.

- 2026-11-18 – S3·S2 (styling, docs) – Plan steps 5 and 6, Session 2 complete. Tailwind CSS
  4 through the Vite plugin, the `@/` alias, shadcn/ui with Radix primitives and the Nova
  preset, `button` as the first component, the ping card with a "Ping again" button and a
  fifth test. The CLI's `shadcn` package sits in the dev dependencies (its stylesheet is
  resolved at build time), so the production audit stays clean. Class sorting through
  Prettier. Docs: ADR-008 and ADR-009 accepted, the frontend half in the README, two ladder
  rows updated. **Next:** Session 3 (theory): TanStack Query and the Tailwind primer.
- 2026-11-17 – S3·S2 (client) – Plan steps 3 and 4 built, two commits. Backend fix first:
  one customizer marks every property of the `*Response` schemas as required, `ProblemDetail`
  declares `status` and `title`; a test pins the naming rule (every schema is a `*Request`, a
  `*Response` or `ProblemDetail`). 83 backend tests, document exported again. Then the client:
  `npm run api:generate` writes `src/api/schema.d.ts`, `src/api/client.ts` creates the
  openapi-fetch client, Vite proxies `/api` to `:8080`, `App` shows the ping answer through a
  plain effect (TanStack Query replaces it in Session 3). As built: base URL is
  `window.location.origin` because Node's `Request` rejects relative URLs in the tests; the
  late `fetch` lookup is needed for stubbing. 4 frontend tests, `npm run check` green, live
  check on `:5173` done. Known and left alone: one dev-only audit finding (`picomatch` under
  Vite and Vitest, no patched version yet; `npm audit --omit=dev` is clean). Upload comes out
  as `requestBody?` with `file: string` in the generated types, a Session 7 topic.
  **Next:** plan step 5: Tailwind 4, the `@/` alias, shadcn/ui init, `button`, the ping card.
- 2026-11-15 – S3·S2 (first half) – Frontend skeleton committed: create-vite 9.3 template
  trimmed, TypeScript pinned to 5.9.3 (the template ships 7.0), oxlint from the template,
  Prettier (`semi: false`, `tabWidth: 4`, lock file ignored), Vitest 4.1 without globals with
  jsdom and Testing Library, one test, `npm run check` as the single gate. Forms will use zod
  through React Hook Form; API responses stay unvalidated at runtime (ADR-008 trade-off).
  **Next:** plan step 3: first the backend fix (response schemas declare their required
  properties, export, own commit), then `api:generate` and the typed client.
- 2026-11-15 – S3·S1 – Sprint 3 opened. Breakdown written (15 sessions in five parts: skeleton
  and generated client, auth in the SPA, file UI with component and browser tests, images with
  compose and Actions, deploy). ADR-008 proposed: openapi-typescript for types plus
  openapi-fetch as the wrapper, query hooks by hand; the generator's peer range pins TypeScript
  to 5.9. Plan in `docs/plans/frontend-skeleton-plan.md`: one origin through the Vite proxy
  from the first day, generated `schema.d.ts` committed with a drift check. A trial run of
  openapi-generator against the document (2,650 generated lines, a beta warning for OpenAPI
  3.1) went into the ADR and found a gap on our side: response schemas list no `required`
  properties, so any generator types response fields as optional; the fix is a backend commit
  in the plan. ADR-009 proposed: Tailwind CSS 4 plus shadcn/ui over MUI and plain CSS
  modules. Test tools settled: Vitest for unit and component, Playwright for the browser rung
  including the two-tab refresh case. The S3-compatible store moves to Sprint 4. The plan now
  has six steps and five commits.
  **Next:** Session 2, step 1 of the plan: the Vite skeleton.
- 2026-11-15 – S2·S11 – Sprint 2 closed. README status line carries the error contract, the OpenAPI
  document and the three-rung suite; everything else of the README pass was already in from the
  sessions that built it. Sprint 2 brought the project from 18 to 82 tests (30 unit, 5 slice, 47
  integration in three contexts), ADR-005 to ADR-007 accepted, five walkthroughs, the OWASP walk,
  and the refresh token flow. Retro and the Sprint 3 order live in the workspace breakdown; the
  short version: frontend against the generated client first, auth in the SPA with the silent
  refresh and the two-tab dedupe, then images, compose, Actions, and EC2 plus Compose behind
  Caddy. **Next:** Sprint 3, Session 1: breakdown, then the Vite skeleton with the generated client.
- 2026-11-15 – S2·S10 – Refresh token rotation built (ADR-007 accepted, 11.11 to 15.11). Plan
  first (`docs/plans/refresh-token-plan.md`), three ADR corrections before any code: cookie
  path `/api/auth` so logout sees it, cleanup as a nightly sweep instead of a login side job,
  the two-tab replay named as a gap. Then bottom-up in four commits: `V3__refresh_token.sql`
  with the family cap folded into `expires_at`, `RefreshToken`, locked `findByTokenHash`
  (`PESSIMISTIC_WRITE`), first JPQL (`deleteExpired`), `Clock` bean, `RefreshTokenService`
  (32 random bytes base64url, SHA-256 hex unsalted, rotate with three outcomes, revoke the
  family) with six unit tests on a fixed clock; `AuthTokens`, `AuthService.refresh` and
  `logout`, `RefreshCookie`, `AuthController` on `ResponseEntity` with `Set-Cookie`,
  `@CookieValue(required = false)`, `RefreshTokenCleanup` on a cron with
  `@EnableScheduling`; six API tests in `RefreshTokenApiIntegrationTest` that play the
  browser by hand (`MockCookie` carried from response to request, an expired row planted via
  the package-level `hash`). The integration rung caught two bugs the mocks could not:
  `rotate` without `@Transactional` (the lock needs one), and the reuse 401 rolling its own
  revocation back (`noRollbackFor`). Docs: ADR-004 trigger 1 fired, README flow and table,
  ladder rows, ERD table and SVG, walkthrough 3 pointer, `invalid-refresh-token` in the
  catalogue, `.http` requests. 82 tests. **Next:** Session 11, close the sprint.
- 2026-11-03 – S2·S8 – OWASP findings closed. Four commits: the upload limit tripped through a
  real server (`RANDOM_PORT`, `RestClient`, a part one byte over 25 MB; MockMvc cannot test it,
  a chunked body made Tomcat drop the connection, a fixed-length body gets the 413 out);
  Spring Security's default headers pinned on JSON and download with the encoded
  `Content-Disposition`; hostile filename stored verbatim, blob under its UUID, filename capped
  at 255 and the part's content type parsed once (`invalid-upload`, `BadRequestException`);
  the generic `IllegalArgumentException` handler removed, framework exceptions answer 500
  without their message; WARN on failed login (masked email) and denied access (caller id),
  both with the request id, asserted through `OutputCaptureExtension`. Dependabot PR merged
  (Boot 4.1.2, springdoc 3.2.0), `mvnw.cmd` renormalized after the bot committed CRLF, both
  Jackson lines pinned for CVE-2026-110879 through the parent's BOM properties. 67 tests, three
  contexts. Ladder: the walk, the WARN lines, breached-password check, wrapper checksum,
  chunked uploads, orphan reconciliation, demo cost controls. Plan in
  `docs/plans/owasp-findings-plan.md`. **Next:** Part 2 retro, decide the refresh-token
  stretch (ADR-007) or close Sprint 2.
- 2026-10-27 – docs – Five walkthroughs in `docs/walkthroughs/`, one page per path through
  the system: a secured request end to end, the two error exits into one advice, credentials
  and the token, startup and wiring, upload with compensation and the twice-checked download.
  Each page: a sequence diagram with lanes for our code, Spring and infrastructure, a numbered
  narrative, who wrote what, where to look, what breaks if a piece is removed, questions the
  page answers. Diagrams are Mermaid source rendered to SVG by `render.sh` (pixel size set
  so browser zoom works, white background so dark mode reads); page 4 is hand-drawn SVG.
  Rechecks while writing fixed four claims before they were published: the membership's
  company is a lazy reference, inserts run at flush, only the request records and the
  principal hide secrets in `toString`, and the entry point reaches the advice but never the
  servlet. README links the folder, ladder row added.
- 2026-10-25 – S2·Session 6 complete – The unit rung. Four Mockito test classes, no Spring
  context: `MembershipGuardTest` (non-member and non-admin both get the neutral membership
  error, admin passes), `AuthServiceTest` (email stripped and lower-cased before lookup and
  save, duplicate stops before hashing, stored hash is the encoder's output, login hands the
  principal to the token service and issues nothing on rejection), `CompanyServiceTest`
  (company and ADMIN membership saved for the same caller and company object, missing caller
  is 403 with nothing saved), `MembershipServiceTest` (duplicate check runs with the found
  user's id, not the caller's; guard failure means no repository call at all). Tools drilled:
  `ArgumentCaptor` on one- and two-argument calls with `eq` on the other position, `thenAnswer`
  returning the saved entity, strict stubs, `verifyNoInteractions`, `ReflectionTestUtils` for a
  generated id. 56/56 (18 unit, 4 slice, 34 full). README names the three rungs and the
  `method_condition_result` naming. **Next:** Session 7 (T), OWASP Top 10 against the code.
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
