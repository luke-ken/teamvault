# Feature plan: refresh token rotation with reuse detection (ADR-007)

ADR-007 settles the shape: an opaque refresh token in a table, rotated on every use, a family
per login, reuse revokes the family, hybrid delivery (access token in the body, refresh token
in an httpOnly cookie). This plan takes the decision down to code. Method as in the other
plans: inventory, forks settled first, bottom-up, tests with each step. Two corrections to the
ADR text fell out of the inventory (forks 2, 4 and 5) and are already applied; only the status
flips to accepted when the build lands.

## Inventory

- `AuthController` has `register` and `login`; `login` returns `TokenResponse(token, expiresAt)`
  as JSON. The Javadoc says logout has no endpoint by design (ADR-004); that sentence goes.
- `AuthService.login` authenticates through the `AuthenticationManager`, hands the principal to
  `TokenService.issue`, logs a masked WARN on failure. `AuthServiceTest` mocks its four
  collaborators and verifies `save` through a captor.
- `TokenService.issue(AuthenticatedUser)` signs the JWT with `Instant.now()`; `JwtProperties`
  carries `secret` and `ttl` (15m) under `teamvault.jwt`. No `Clock` bean exists anywhere.
- `SecurityConfig`: CSRF off with the comment "no cookie or session auth", `/api/auth/**`
  permitAll, stateless sessions. The comment needs one more sentence once a cookie exists.
- `ApiExceptionHandler` maps `AuthenticationException` to 401 `bad-credentials` and
  `InvalidBearerTokenException` to 401 `invalid-token` with a `WWW-Authenticate: Bearer`
  challenge. `ApiException` has four subclasses (409, 403, 404, 400), none for 401.
- Persistence style: entities with `@UuidGenerator` ids, `@ManyToOne(LAZY)` to `AppUser`,
  `@CreationTimestamp`; repositories are derived queries only, no `@Query` in the codebase.
  Flyway owns the schema, Hibernate validates (`V1__init.sql`, `V2__seed.sql`).
- Tests: `AbstractIntegrationTest` (MockMvc, one cached context, real login per user per run),
  `AuthApiIntegrationTest` with seven tests, `OutputCaptureExtension` already in use. 67 tests.
- `docs/openapi.yaml` is exported by a test from the controllers; two new endpoints change it.
  `backend/http/teamvault.http` has login and the secured calls.

## Design forks, settled

1. **Table and the family cap folded into one column.** `V3__refresh_token.sql`:

   ```sql
   CREATE TABLE refresh_token (
       id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
       user_id           uuid        NOT NULL REFERENCES app_user (id),
       family_id         uuid        NOT NULL,
       token_hash        text        NOT NULL,
       expires_at        timestamptz NOT NULL,
       family_expires_at timestamptz NOT NULL,
       used_at           timestamptz,
       revoked_at        timestamptz,
       created_at        timestamptz NOT NULL DEFAULT now(),
       CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
   );
   CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
   CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);
   ```

   `family_expires_at` is set once at login (`now + 30d`) and copied to every successor, so a
   row's `expires_at` is `min(now + 14d, family_expires_at)` at insert time. The lookup then
   checks one column, and cleanup stays `expires_at < now`. The unique on the hash serves the
   lookup. The family index serves revocation, the expiry index serves the nightly sweep.
   `docs/erd.md` gets the table and the two index justifications.
2. **Cookie path is `/api/auth`, not `/api/auth/refresh`.** The ADR scoped the cookie to the
   refresh path, but logout needs it too: the cookie is the only thing that names the family.
   `Path=/api/auth` reaches register, login, refresh and logout; the first two ignore it. No
   exposure gained: all four are public endpoints and the cookie is `SameSite=Strict`. Name
   `refresh_token`, flags `HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=14d`.
   `Secure` comes from `teamvault.jwt.refresh-cookie-secure` (default `true`), set to `false`
   in `application-dev.yaml` so the `.http` file works on `http://localhost`. The test profile
   keeps the default, so the integration test asserts all four flags. The ADR decision text
   carries the corrected path.
3. **Two services, one orchestrator.** New `auth/RefreshTokenService` owns the table: random
   bytes, hash, insert, rotate, revoke; collaborators `RefreshTokenRepository` and a `Clock`.
   `AuthService` orchestrates: `login` authenticates, issues the access token and asks for a
   refresh token; `refresh` rotates and issues a new access token for the row's user; `logout`
   revokes. Login and refresh return `AuthTokens(TokenResponse access,
   String refreshToken)`; the controller turns the second half into a `Set-Cookie` header via
   `ResponseEntity`, with a small `RefreshCookie` component that builds the set and the clear
   cookie from `JwtProperties`. The body stays `TokenResponse`, so curl and the `.http` file see
   no change. `JwtProperties` grows `refreshTtl` (14d), `refreshFamilyTtl` (30d),
   `refreshCookieSecure` (true). A `Clock` bean (`Clock.systemUTC()`) in `JwtConfig`;
   `TokenService` keeps `Instant.now()` for now, out of scope.
4. **One lookup, locked, three outcomes.** `findByTokenHash` carries
   `@Lock(LockModeType.PESSIMISTIC_WRITE)` so two refreshes with the same token serialise:
   the second waits, then reads `used_at` set, then takes the reuse path. Outcomes:

   | Row found | Condition | Does | Answers |
   |---|---|---|---|
   | live | `used_at` null, `revoked_at` null, `expires_at` in the future | set `used_at`, insert successor (same family, same `family_expires_at`) | 200, new access token, new cookie |
   | used | `used_at` set | set `revoked_at` on the family's live row | 401 |
   | revoked, expired, none, no cookie | anything else | nothing | 401 |

   All 401s share one new `ProblemType.INVALID_REFRESH_TOKEN` (`invalid-refresh-token`,
   detail "Invalid or expired refresh token") thrown as `common/UnauthorizedException`, the
   fifth `ApiException` subclass. No `WWW-Authenticate` challenge: the credential is a cookie,
   not a bearer header. The client logic needs no finer distinction: a 401 from any API call
   means try a refresh, a 401 from the refresh means log in again. Known gap, named in the ADR
   trade-off: two tabs that refresh with the same cookie in the same instant are a reuse to the
   server, and the family dies. The SPA dedupes refreshes across tabs in Sprint 3 (Web Locks or
   `BroadcastChannel`); a server-side leeway window is the fallback if that is not enough.
5. **Cleanup is a scheduled sweep, not a login side job.** The ADR named a login-time delete
   as the cheapest form; dropped here because the login would do two jobs and a failing delete
   would fail the login. Instead `auth/RefreshTokenCleanup` with
   `@Scheduled(cron = "0 0 3 * * *")` calls one bulk statement, the first JPQL in the codebase:
   `@Modifying @Query("delete from RefreshToken r where r.expiresAt < :now")`. A derived
   `deleteBy...` would load every entity first; a client that refreshes every 15 minutes leaves
   96 rows a day, so one statement matters. `@EnableScheduling` on a new `config/SchedulingConfig`.
   Status is not part of the condition (an expired row can do nothing). Cron, not fixed rate: the
   wall clock survives restarts, an interval would reset with every deploy. A missed night costs
   nothing, and two instances running the same delete is harmless, so no lock. The sweep logs the
   row count at INFO. The Sprint 4 sweep row is done early.
6. **Logout is idempotent.** `POST /api/auth/logout` reads the cookie; present and known, the
   family's live row is revoked; missing or unknown, nothing happens. Both answer 204 with the
   clearing cookie (`Max-Age=0`, same path and flags). No bearer token needed: the cookie is the
   credential, and a browser that lost its access token on reload must still be able to log out.
7. **Where the hash lives.** `RefreshTokenService` holds a `SecureRandom`, generates 32 bytes,
   base64url without padding as the raw token, SHA-256 hex (`HexFormat`) as the stored hash.
   Unsalted on purpose and commented with the ADR's reason. The hash function is a package-level
   static so the integration test can plant an expired row for a raw token it knows.
8. **Tests by rung.** Unit, `RefreshTokenServiceTest` (repository mocked, fixed `Clock`):
   `issue_storesHashNotRawToken_expiryIsIdleTtl_familyCapSet`,
   `rotate_liveToken_markedUsed_successorSharesFamilyAndCap`,
   `rotate_nearFamilyCap_successorExpiresAtTheCap`, `rotate_usedToken_revokesFamily_401`,
   `rotate_revokedOrExpiredOrUnknown_401_nothingWritten`, `revoke_liveRowOfTheFamily`,
   `AuthServiceTest.login_issuesRefreshToken_nextToTheAccessToken`;
   `RefreshTokenCleanupTest.sweep_deletesByExpiryOnly_logsTheCount`. Integration, in
   `AuthApiIntegrationTest`: `login_setsRefreshCookie_httpOnlySecureStrictScopedToAuth`,
   `refresh_rotates_oldCookieRejectedAfterwards`,
   `refresh_reuse_revokesFamily_nextRefreshWithTheNewCookie401`,
   `refresh_withoutCookie_401_invalidRefreshToken`, `refresh_expiredRow_401` (row planted via
   the repository with a past `expires_at`), `logout_204_clearsCookie_refreshAfterwards401`.
   Each integration test registers its own user so the rows do not collide with the cached
   bearer logins of other classes. Expected count: 67 plus 14.
9. **Docs.** ADR-007 status to accepted (path, two-tab note and sweep are already in the
   text). `docs/errors.md`
   entry `invalid-refresh-token`. README: the auth flow paragraph gains refresh and logout, the
   "deliberately not built" bullet on refresh tokens becomes a bullet on the denylist and the
   two-tab leeway. Ladder row "Access token in memory, refresh token rotation in an httpOnly
   cookie" to built with the note that the in-memory half is the Sprint 3 frontend line.
   ADR-004 trigger 1 marked as fired, answered by ADR-007. `docs/openapi.yaml` re-exported.
   `.http` file gets refresh and logout requests. `docs/erd.md` gets the table. Walkthrough 3
   gets a pointer, no new walkthrough: the trace tables live in ADR-007 and the course note.

## Build order

### Commit 1: `feat: refresh token rotation with reuse detection, httpOnly cookie, logout`
1. `V3__refresh_token.sql`; `auth/RefreshToken` entity; `auth/RefreshTokenRepository` with the
   locked `findByTokenHash`, `findByFamilyIdAndUsedAtIsNullAndRevokedAtIsNull`, the JPQL delete.
   Flyway and `ddl-auto: validate` check the mapping on the first full test.
2. `JwtProperties` grows the three fields; `Clock` bean in `JwtConfig`; `application-dev.yaml`
   sets `refresh-cookie-secure: false`.
3. `ProblemType.INVALID_REFRESH_TOKEN`; `common/UnauthorizedException`.
4. `auth/RefreshTokenService` plus `RefreshTokenServiceTest` (unit rung first, no Postgres).
5. `AuthTokens` record; `AuthService.login` extended, `refresh` and `logout` added;
   `AuthServiceTest` grows the login case and the mock; `auth/RefreshTokenCleanup` plus
   `config/SchedulingConfig` and `RefreshTokenCleanupTest`.
6. `auth/RefreshCookie`; `AuthController` returns `ResponseEntity<TokenResponse>` for login
   and refresh, `ResponseEntity<Void>` for logout; Javadoc sentence on logout replaced.
7. `AuthApiIntegrationTest` six new tests; `SecurityConfig` CSRF comment extended.
8. `docs/errors.md`; `docs/openapi.yaml` from the export test; `.http` file.

### Commit 2: `docs: ADR-007 accepted, refresh flow in the README, ladder and ERD`
9. ADR-007 status; ADR-004 trigger 1; README; ladder row; `docs/erd.md`;
   `CLAUDE.md` state; `PROGRESS.md` entry.
