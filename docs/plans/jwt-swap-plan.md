# Feature plan: JWT swap (ADR-004)

How a feature is approached here, using the HTTP Basic to JWT swap as the worked example.
Decision in `../adr/004-self-issued-jwt-auth.md`; this file is the build order.

## Method

1. **Inventory first, code second.** Read every file the feature touches and split the
   work into *new* and *changed*. New parts can be added without breaking anything;
   changed parts are where the risk sits.
2. **Bottom-up in dependency order.** Things with no dependencies first (config, keys),
   then what consumes them (encoder/decoder, token service), then the HTTP surface
   (login endpoint), and only at the end the switch that everything hangs on (the filter
   chain). The build and the existing tests stay green after every step; the risky
   change becomes one small commit at the end.
3. **Settle the design forks before the first line.** Here: principal carries the user
   id (no extra lookup at login); the ping slice test mocks the decoder (it should not
   care about keys).
4. **Tests migrate, assertions stay.** The existing tests change only their auth helper.
   Identical assertions are the proof that the mechanism changed and nothing else.
5. **Docs close the loop.** README flow, ADR status to accepted, PROGRESS.

## Build order

Status 2026-09-20: steps 1 to 6 done and committed (3 commits). Next: step 7b
(`LoginRequest`), 8, 9.

### New (build stays green throughout)
1. `pom.xml`: `spring-boot-starter-oauth2-resource-server` (brings Nimbus encoder/decoder).
2. `auth/JwtProperties`: validated `@ConfigurationProperties("teamvault.jwt")`, `secret`
   (min 32 chars) + `ttl` (default 15m). `@ConfigurationPropertiesScan` on the app class.
   Fail-fast at boot when the secret is missing.
3. Config: `application.yaml` (`teamvault.jwt.*`, `.env` import), `.env.example`,
   `.gitignore` gets `.env`. **Pulled forward from step 11:** a fixed test secret in
   `src/test/resources/application-test.yaml` + `@ActiveProfiles("test")` on the Spring
   Boot tests, because fail-fast config would otherwise kill context start in tests.
   Lesson: a fail-fast config change drags its test config along in the same step.
4. `auth/JwtConfig`: `JwtEncoder` + `JwtDecoder` beans (HS256, issuer validated).
   Separate from `SecurityConfig` so slice tests can import one without the other.
5. `user/AuthenticatedUser`: `UserDetails` carrying the user UUID. Step 13 pulled in
   here: safe, because HTTP Basic accepts any `UserDetails`.
6. `auth/TokenService`: claims `sub` (UUID), `email`, `iat`, `exp`, `iss`; returns
   `TokenResponse` (record pulled in from step 7 so the service returns the API type).
7. `auth/LoginRequest`, `auth/TokenResponse` records.
8. `auth/AuthController`: `POST /api/auth/login`. Needs an `AuthenticationManager` bean
   (`ProviderManager` over `DaoAuthenticationProvider`) in `SecurityConfig`.
9. `auth/JsonAuthenticationEntryPoint`: 401 as the existing `ApiError` JSON plus
   `WWW-Authenticate: Bearer`. Filter-chain failures never reach the controller advice.
10. `AuthApiIntegrationTest`: login 200 · wrong password 401 · unknown email 401 (same
    message, no enumeration) · no token 401 + header · garbage token 401 JSON ·
    login then bearer then file list 200.
11. Test secret: done in step 3.

### Changed (old tests break at step 12, green again at step 17)
12. `SecurityConfig`: `httpBasic` out, `oauth2ResourceServer(jwt)` in, permit
    `/api/auth/**`, entry point wired, `AuthenticationManager` bean.
13. `AppUserDetailsService`: returns `AuthenticatedUser`.
14. `FileController`: `@AuthenticationPrincipal Jwt`, caller id from `sub`.
15. `FileService`: `callerEmail` becomes `callerId`; `requireMembership` queries
    membership directly; `AppUserRepository` field goes. One query less per request.
16. `ApiExceptionHandler`: `AuthenticationException` to 401 (login failures do reach
    the advice).
17. `FileApiIntegrationTest`: `httpBasic(...)` becomes a bearer helper (login once per
    user, cache the token). Assertions unchanged.
18. `PingControllerTest`: `@MockitoBean JwtDecoder`.
19. README: intro, curl flow with bearer, API table, JWT out of "deliberately not built".
20. ADR-004 status to accepted, in the feature commit.

## Diagram (to do)
One picture, two lanes:
- **Login:** client → `POST /api/auth/login` → `AuthenticationManager` →
  `AppUserDetailsService` (bcrypt check) → `TokenService` (`JwtEncoder`) → `{token}`.
- **Request:** client → `Authorization: Bearer` → `BearerTokenAuthenticationFilter` →
  `JwtDecoder` (signature, exp, iss) → `Jwt` principal → controller → service
  (`membership` lookup = ADR-003) → response. 401 branch at the decoder (entry point),
  403 branch at the service.
