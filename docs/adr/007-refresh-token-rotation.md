# ADR-007: Opaque refresh tokens with rotation and reuse detection

- **Date:** 2026-11-05
- **Status:** proposed

## Context
ADR-004 issues a 15-minute access token and nothing else; a client logs in again when it
expires. Its first revisit trigger is the React frontend (Sprint 3): a login every 15 minutes
is unacceptable behind a UI, so the token needs renewal without a password. Renewal means a
second, long-lived credential, and a long-lived credential must be revocable and must make a
copy of itself detectable. Those two requirements decide most of the shape below. The decision
is pulled into Sprint 2 so the frontend starts against a finished auth API.

## Options considered
1. **Buy it: Keycloak or another identity provider.** Brings refresh, rotation, revocation,
   a login page and RS256 in one move. Cons: a second container and a second configuration
   surface in a project whose point is to build and explain the mechanism; the "deliberately
   not built" list keeps the IdP as the production answer and names this as the learning
   answer.
2. **Refresh token as a second JWT.** Signed, self-contained, verified like the access token.
   Pros: no new table for verification. Cons: rotation and reuse detection are lookups by
   nature (was this token used before, is this family revoked), so the server consults a table
   on every refresh anyway; the signature then adds size and a second verification path and
   removes nothing.
3. **Opaque refresh token in a table.** 32 random bytes, base64url, the SHA-256 hash stored in
   `refresh_token` with user, family, expiry, `used_at`, `revoked_at`. One lookup by hash per
   refresh with three outcomes: live (rotate), used (revoke the family, 401), anything else
   (401). Pros: the cheapest stateful token, revocation and detection fall out of the row
   shape, the table is worthless without the raw token. Cons: a table and a cleanup rule.
4. **Where the tokens live in the browser.** Both in cookies puts every mutating endpoint
   under CSRF rules and rebuilds a session with extra steps. Access token in the JSON body,
   refresh token only as a cookie scoped to the refresh path keeps the API client-agnostic
   (curl, the `.http` file, mobile, browser all use the bearer header) and shrinks CSRF
   exposure to one endpoint whose response a cross-site caller cannot read.

## Decision
Option 3 with option 4's split. `POST /api/auth/login` returns the access token in the body
and sets the refresh token as a cookie `HttpOnly; Secure; SameSite=Strict;
Path=/api/auth/refresh`. `POST /api/auth/refresh` reads the cookie, marks the row used and
inserts the successor in one transaction, returns a new access token and the new cookie. A
used token presented again revokes the whole family. `POST /api/auth/logout` revokes the
family and clears the cookie. Refresh tokens expire 14 days after issue; a family ends 30
days after its first row regardless. Expired rows of the logging-in user are deleted during
login; a scheduled sweep joins in Sprint 4. The hash is unsalted SHA-256 on purpose: the token
is 256 random bits, not a password, so a slow salted hash would protect nothing.

## Trade-off (why this, what we give up)
The API is no longer stateless on one path: the refresh call reads and writes a table, once
per 15 minutes per client. Every other call is still verified by signature without a lookup,
so the cost sits where it pays. We give up instant logout as before: the access token in
flight works for its remaining minutes, and after a refresh two access tokens of one family
overlap briefly. Both are the accepted gap that the Sprint 4 denylist would close. We own more
security-critical code than ADR-004 already made us own (token generation, rotation, the
reuse rule); mitigated by small surface, one service, and tests for every outcome of the
lookup. A reload of the frontend starts with a silent refresh because the access token lives
in memory; that is a Sprint 3 line in the React app. Revisit: an external identity provider
replaces this ADR wholesale (ADR-004 trigger 2), and `SameSite=Strict` is reconsidered if the
frontend ever moves to another site than the API.
