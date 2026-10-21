# ADR-005: Problem Details as the error contract, request id on every response

- **Date:** 2026-10-21
- **Status:** proposed

## Context
Every failure path answers one JSON shape, `{status, error, message}`, written by the
controller advice and, by hand, by the security entry point (ADR-002, ADR-004). That shape
has two weaknesses now that a frontend is one sprint away. A client can only tell two
errors with the same status apart by comparing `message` text, which is wording and will
change. And nothing ties an error a user sees to the log lines that explain it; with two
backend instances, time plus email stops being a key.

RFC 9457 (Problem Details for HTTP APIs) is the standard body for exactly this:
`type`, `title`, `status`, `detail`, `instance`, extension members, served as
`application/problem+json`. Spring supports it natively (`ProblemDetail`,
`ResponseEntityExceptionHandler`), and the OpenAPI document in this sprint can declare
one error schema for every operation.

## Options considered
1. **Keep the custom shape, add a `code` field and a `requestId` field.**
   - Smallest change; the tests keep their assertions.
   - A home-grown contract the frontend client and the OpenAPI document have to describe
     from scratch; no standard media type to detect an error body by.
2. **Problem Details through Spring's base handler.**
   - Standard shape and media type; the framework's own errors (unknown route, wrong
     method, unsupported media type, unreadable body, validation) arrive in the same shape
     with no code per case.
   - Every error test changes its assertions once; the entry point has to produce the
     same body although it runs outside Spring MVC.
3. **Problem Details written by hand in the advice, no base class.**
   - Full control over every body.
   - Re-implements what the base class gives, and the framework errors stay in Boot's
     default `/error` shape unless each gets a handler.

Inside option 2, three smaller forks:

- **`type` namespace.** `about:blank` only (no machine key at all); a URN
  (`urn:teamvault:problem:x`, never resolvable); an anchor into the repository's error
  catalogue on GitHub (resolvable, zero infrastructure); a future own domain (not owned
  today, would change the contract later).
- **Entry point body.** Serialise a `ProblemDetail` with the application `ObjectMapper`,
  or delegate the exception to Spring MVC's `HandlerExceptionResolver` so the advice writes
  it. Checked in the Spring 7 jars: the Jackson mixin that flattens `ProblemDetail`
  extension members is registered inside the MVC message converter, not on Boot's mapper
  bean. The hand-written path would need the mixin registered by hand, or ship
  `requestId` nested under `properties`.
- **Request id origin.** A servlet filter first in the chain, or Micrometer Tracing.
  Tracing follows one action across services with timing per hop; TeamVault has one
  service and nothing to hop to.

## Decision
**Option 2.** `ApiExceptionHandler` extends `ResponseEntityExceptionHandler`; Boot's
`spring.mvc.problemdetails.enabled` stays off because the base class bean already makes
Boot's own advice back off, and two advices for one exception is the thing to avoid. All
base handlers funnel through `handleExceptionInternal`, which is overridden once to add
`requestId`; only validation (field list as an `errors` extension) and the unreadable body
(own wording) get a dedicated override.

**`type` is a GitHub anchor** into `docs/errors.md`:
`https://github.com/luke-ken/teamvault/blob/main/docs/errors.md#membership-required`. The
slug is the contract a client switches on; the page is documentation. Slugs live in one
enum (`ProblemType`) together with their status, and API exceptions carry their type, so a
throw site names the error kind, not the status. The generic 500 keeps `about:blank`, where
the status is all the client is told.

**The entry point delegates.** `JsonAuthenticationEntryPoint` hands the security exception
to the MVC `handlerExceptionResolver`; the advice maps "no token" and "invalid token" to
their own types and sets `WWW-Authenticate: Bearer`. There is one writer of error bodies in
the application. The "one shape from both exits" rule from Sprint 1 becomes structural
instead of a convention kept by hand.

**Request id by filter.** `RequestIdFilter` runs at highest precedence, before Spring
Security, so the 401 path has the id in its logs and its body. A client-supplied
`X-Request-Id` is kept only when it matches a conservative pattern (length cap, safe
characters), otherwise replaced; unvalidated it would be a log injection vector. The id is a
response header on every answer, success included, and an extension member on every
problem body. Logs print it through `logging.pattern.correlation`, the knob Micrometer
Tracing takes over when a second service makes tracing worth running.

## Trade-off (why this, what we give up)
Gained:
- A standard error body with a detectable media type; one error schema in OpenAPI; a
  machine key per error kind that survives rewording.
- Framework errors in the same shape for free, request id included.
- One writer: the security exit cannot drift from the MVC exit.
- A support case finds its log lines with one string, on any instance.

Given up:
- **`type` points at a repository path.** Moving or renaming the repo breaks the courtesy
  link, not the contract: clients compare the string, they do not fetch it. Slugs are
  frozen once published; a rename is a breaking change.
- **The entry point depends on Spring MVC.** It calls the resolver bean, so it no longer
  works in a context without MVC. Every context here has MVC.
- **Every error test changed once.** `$.message` became `$.detail`, validation tests
  assert the `errors` list.
- **A filter id is not a trace.** It identifies one request in one service. The moment a
  second service exists, Micrometer Tracing replaces the filter and the `requestId` member
  becomes the trace id. Named on the production ladder.

Revisit triggers: a second service (tracing); a public API with external clients (an own
domain for `type`, versioned catalogue); a frontend form library that wants a different
`errors` shape than `{field, message}`.
