# ADR-002: Spring Security on from day 1, deny-by-default

- **Date:** 2026-07-05
- **Status:** accepted

## Context
The sprint plan schedules auth (JWT) for later in Sprint 1, so the starter dependency could
have waited. But security added late tends to be bolted on: endpoints get written assuming
they're public, and the "lock it down" pass then breaks things or gets skipped.

## Options considered
1. **Add `spring-boot-starter-security` only when the auth session starts** — fewer moving
   parts now; every endpoint is reachable during early development. Risk: retrofitting.
2. **Include it now with a deny-by-default `SecurityFilterChain`** — every new endpoint is
   locked until explicitly permitted (`/api/ping` is the only permit so far). Cost: each dev
   endpoint needs a conscious permit/auth decision, and until JWT lands, protected routes
   answer 403 (no auth entry point is configured yet).

## Decision
Option 2. `SecurityConfig` permits `/api/ping`, requires authentication for everything else.
The JWT session will replace "authenticated how?" (currently: no way at all) with a real
mechanism and a proper 401 entry point.

## Trade-off (why this, what we give up)
Slight early friction (can't casually curl new endpoints) in exchange for fail-closed
defaults — forgetting to configure security on a new endpoint means it's unreachable, not
exposed. That's the OWASP-friendly failure mode for a multi-tenant app.