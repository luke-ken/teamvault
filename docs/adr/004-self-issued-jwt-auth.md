# ADR-004: Self-issued JWT as the authentication mechanism

- **Date:** 2026-09-20
- **Status:** proposed

## Context
ADR-002 locked every endpoint down but left "authenticated how?" open. The interim answer
was HTTP Basic against bcrypt `app_user` rows, with JWT named as the swap from day one.

HTTP Basic sends the username and password with every single request. That is tolerable
for `curl` during development, but a real client would have to keep the password around
for the lifetime of the session and expose it on every call. With a token, credentials
leave the client exactly once, at login; afterwards only the token travels.

The API is stateless by design (README: horizontal scaling without sticky sessions), so
the mechanism must not quietly reintroduce server-side state.

ADR-003 already decides *authorization*: tenant scope comes from the caller's membership,
looked up in the service layer on every request. This ADR only decides *authentication*:
how a caller proves who they are.

It is decided now, at the close of Sprint 1, because the choice shapes the login endpoint,
the filter chain, and every future client, starting with the React frontend in Sprint 3,
which should never need to store a password.

## Options considered
1. **Server-side sessions (cookie with an opaque session id)**
   - Needs a shared session store. In-memory breaks the moment a second instance runs; a
     session table or Redis fixes that, but then the store scales with the app.
   - Every request costs a session lookup.
   - Revocation is trivial: row gone means logged out immediately.
   - The client holds only an opaque id that reveals nothing.
   - Cookies bring CSRF into scope; bearer tokens do not, but they move the concern to
     where the client stores the token (XSS).
   - Worth it for a small server-rendered app on one instance with no scaling
     requirement, or when instant logout is a hard requirement.
2. **External identity provider (Keycloak)**
   - Stateless on the app side, stateful inside Keycloak.
   - Brings almost everything: tokens, refresh tokens, revocation, rotation, reuse
     detection, even the login page.
   - Loose coupling: Keycloak upgrades and configuration changes do not touch the app.
   - The cost is operational: one more system to run, secure, configure and back up. For
     a portfolio app with no frontend yet, the login page is a benefit without a user.
   - Worth it when a second service must verify tokens, or when a frontend needs real
     login flows (SSO, MFA, password reset).
3. **Self-issued JWT, signed and verified by TeamVault**
   - Everything stays in the app. Requests are stateless: any instance verifies any token
     with nothing but the signing secret.
   - Custom claims are easy to add. Lowest infrastructure footprint.
   - The app owns what an IdP would otherwise own: secret generation, storage and
     rotation; algorithm choice; the revocation gap.
   - Within this option: a hand-rolled filter with a JWT library, or Spring Security's
     own `oauth2-resource-server` with the Nimbus encoder and decoder. See Decision.

## Decision
**Option 3**, implemented with Spring Security's resource-server support
(`NimbusJwtEncoder` / `NimbusJwtDecoder`), not a hand-rolled filter.

**Why the resource-server machinery:** a Keycloak-protected app *is* a resource server:
it validates JWTs that Keycloak issued. Spring's resource-server support is the standard
way to do that validation, at a level of abstraction where the token's origin does not
matter. Validating our own tokens through the same machinery means the later upgrade is
a decoder configuration swap (HMAC secret out, issuer URL in) with no filter code to
rewrite. A hand-rolled filter would do the same verification by hand today and be thrown
away on that day. This is what turns "upgrade path" from a hope into a design property.

**Login:** `POST /api/auth/login {email, password}` authenticates against the existing
bcrypt `app_user` rows via the `AuthenticationManager` and answers `{token, expiresAt}`.
HTTP Basic is removed from the filter chain.

**Algorithm: HS256.** One service both issues and verifies, so a symmetric secret gives
the same guarantee as a key pair with nothing extra to manage. The known weakness of
HS256, that anyone who can verify a token can also forge one, does not apply here: the
only party able to verify is TeamVault, which is the party entitled to issue anyway.
"Forge" and "issue" are the same act. The secret is still a secret, and leaking it is as
bad as leaking an RS256 private key would be, but sharing it with no one adds no trust
that was not already there. RS256 becomes the better choice the moment a verifier lives
in a different trust domain: a second service, or an external IdP publishing a public key
set. That is exactly the Keycloak path, and it is reachable through the same decoder
swap.

**Claims: identity only.** `sub` = user UUID (stable even if the email changes), `email`,
`iat`, `exp`, `iss`. No memberships, no roles.

Roles in TeamVault are per membership (alice is ADMIN in Acme, bob is MEMBER in Globex),
so there is no global role that could go into a token. ADR-003 already looks membership
up per request in the service layer. The token therefore proves *who*; the database
decides *what*, freshly, on every request. If a user is removed from a company, the
service layer sees it on their very next call and denies access (403), even though the
token they hold is still valid. Nothing about authorization is ever stale.

What stays unrevocable is identity itself: a disabled or deleted account keeps a working
token until it expires. That window is exactly the token lifetime, which is why a short
TTL is an acceptable answer rather than a workaround.

**Lifetime:** 15 minutes by default, configurable (`teamvault.jwt.ttl`). No refresh
token, see Trade-off.

**Secret handling:** the signing secret comes from the environment
(`TEAMVAULT_JWT_SECRET`), never from code. A committed `.env.example` documents the keys
with placeholder values; the real `.env` is gitignored and imported optionally, so CI and
production use plain environment variables. The app fails at boot if the secret is
missing or shorter than 32 characters (validated `@ConfigurationProperties`). Tests set a
fixed test-only secret in test configuration, so `./mvnw test` stays independent of the
developer's shell. Best practice at scale is a managed secret store (Azure Key Vault, AWS
Secrets Manager); at TeamVault's size an environment variable is the honest substitute,
and a secret store would inject the same variable later without touching the app.

**Failure shape:** a missing, malformed or expired token gives 401 with the existing JSON
error body and a `WWW-Authenticate: Bearer` header. A valid token without membership in
the requested company stays 403, as today. This closes the gap ADR-002 named: protected
routes answered 403 for lack of an auth entry point.

## Trade-off (why this, what we give up)
Gained:
- Stateless authentication: no session store, no sticky sessions, any instance verifies
  any token.
- Lowest infrastructure: nothing to run beyond the app and its database.
- Credentials cross the wire once per login instead of once per request, and no client
  ever needs to hold a password.
- Upgrade path to an IdP is a configuration change, not a rewrite, because verification
  already goes through Spring's resource-server support.

Given up:
- **No revocation before expiry.** A stolen or no-longer-wanted token stays valid for up
  to 15 minutes. Mitigated by the short TTL and by identity-only claims: authorization is
  always fresh from the database, so the damage is bounded to what the identity could do
  anyway.
- **No refresh token.** Building one costs about as much as the access token itself, plus
  rotation and reuse-detection rules, and it only pays off once a frontend exists to do
  silent re-authentication. Until then, logging in again is the simpler answer.
- **Key rotation is not built.** Changing the secret invalidates every token at once.
  Acceptable now; an IdP or a key-id (`kid`) scheme fixes it later.
- **The app owns security-critical code** (token issuing, secret validation) that an IdP
  would own for us. Mitigated by using Spring's implementation rather than our own.

Revisit triggers, in the order they are likely to arrive:
1. **The React frontend lands (Sprint 3).** A 15-minute login is unacceptable in a UI, so
   a refresh token becomes necessary: either built here (rotation, reuse detection) or
   bought by switching to Keycloak, which also brings the login page the frontend needs.
2. **A second service needs to verify tokens.** HS256 would force it to hold the secret
   and therefore to be able to forge; switch to RS256 or to an IdP.
3. **Instant logout becomes a requirement.** A token denylist in Redis (planned for
   Sprint 4 anyway) or a return to server-side sessions.
