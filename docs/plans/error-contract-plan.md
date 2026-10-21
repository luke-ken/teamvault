# Feature plan: error contract (ADR-005)

Problem Details (RFC 9457) replaces the custom `{status, error, message}` body, and every
response carries a request id. Decision in `../adr/005-error-contract-problem-details.md`;
this file is the build order. Method as in `jwt-swap-plan.md`: inventory first, bottom-up,
forks settled before the first line, tests migrate with their assertions intact where the
mechanism did not change.

## Inventory

Writes an error today:
- `common/ApiExceptionHandler` (advice): 400, 401 bad credentials, 403, 404, 409, 413, 500.
- `auth/JsonAuthenticationEntryPoint` (security chain): 401 no token, 401 bad token, written
  by hand with the application `ObjectMapper`.

Throws an API exception (eight sites): `FileService` (409, 404), `AuthService` (409),
`MembershipService` (404, 409), `MembershipGuard` (403, 403), `CompanyService` (403).

Asserts on the old shape: `$.message` in `AuthApiIntegrationTest` (2), `CompanyApiIntegrationTest`
(2), `MembershipApiIntegrationTest` (2). README shows three error bodies and describes the shape.

## Design forks, settled

1. **Base class, not the property.** `ApiExceptionHandler extends ResponseEntityExceptionHandler`.
   Boot's `spring.mvc.problemdetails.enabled` registers its own advice only when no
   `ResponseEntityExceptionHandler` bean exists, so the property stays off and there is exactly
   one advice. The base class already answers unknown route (404), wrong method (405), wrong
   media type (415), unreadable body, validation and type mismatch with a `ProblemDetail`.
2. **One decoration point.** Every base handler funnels through `handleExceptionInternal`.
   Overriding it adds `requestId` to whatever `ProblemDetail` the base built, so framework
   errors carry the id without one handler each. Only validation (field list) and the
   unreadable body (our wording) get their own override.
3. **`type` namespace: GitHub anchors.** `https://github.com/luke-ken/teamvault/blob/main/docs/errors.md#<slug>`,
   catalogue in `docs/errors.md`. The slug is contract; the page is a courtesy. Generic 500
   keeps `about:blank`. Slugs live in one enum, `ProblemType`, with their status.
4. **The entry point delegates.** Checked in the jars: Spring 7 registers the
   `ProblemDetailJacksonMixin` inside `JacksonJsonHttpMessageConverter`, not on Boot's
   `ObjectMapper` bean. Writing a `ProblemDetail` with the injected mapper would nest
   `requestId` under `properties`. So the entry point calls the MVC `HandlerExceptionResolver`
   with the security exception and the advice writes the body. One writer, not two callers
   of one factory. The advice maps `InsufficientAuthenticationException` (no token) and
   `InvalidBearerTokenException` (bad token) to their own types and sets `WWW-Authenticate`.
5. **Exceptions carry their type.** `ApiException(ProblemType, detail)` is the base;
   `NotFoundException`, `ConflictException`, `ForbiddenException` stay as thin subclasses so
   the eight throw sites change only where the slug differs (the two guard 403s, the
   `CompanyService` 403).
6. **Request id by filter, first in the chain.** `RequestIdFilter` is a `OncePerRequestFilter`
   bean at `Ordered.HIGHEST_PRECEDENCE`, ahead of Spring Security's chain (order -100).
   Incoming `X-Request-Id` is kept only if it matches `[A-Za-z0-9._-]{8,64}`, else a UUID.
   Header on every response; MDC put and remove around the chain. Log line via
   `logging.pattern.correlation`, the same knob Micrometer Tracing takes over later.
7. **Validation extension.** `errors`: a sorted list of `{field, message}`. Detail stays
   "Validation failed". The form library in Sprint 3 maps the list by field.

## Build order

### New (build and tests stay green)
1. ADR-005, `docs/errors.md` (this commit, docs only).
2. `common/ProblemType` enum (slug, status), `common/ApiException` base, the three subclasses
   on top of it. Throw sites updated for the three 403s. `common/RequestIdFilter`.
   `logging.pattern.correlation` in `application.yaml`.
   Commit: `feat: request id filter, problem types on API exceptions`.

### Changed (the switch, one commit)
3. `ApiExceptionHandler` extends `ResponseEntityExceptionHandler`: `handleExceptionInternal`
   decoration, overrides for validation and unreadable body, handlers for `ApiException`,
   `DataIntegrityViolationException`, `IllegalArgumentException`,
   `MaxUploadSizeExceededException`, `InsufficientAuthenticationException`,
   `InvalidBearerTokenException`, `AuthenticationException`, `Exception`. `ApiError` record
   deleted. `Problems` helper builds the body from a `ProblemType`.
4. `JsonAuthenticationEntryPoint` delegates to the `handlerExceptionResolver` bean; no mapper.
5. Test migration: six `$.message` assertions become `$.detail` or `$.errors[0].field`.
   Commit: `feat: Problem Details error contract (ADR-005)`.

### Tests (own commit)
6. `common/ErrorContractApiIntegrationTest` (shared context): no token, bad token, wrong
   password, non-member, unreadable body, unknown route with a valid token; each asserts
   `application/problem+json`, `type`, `title`, `status`, `detail`, `instance`, `requestId`,
   and `WWW-Authenticate: Bearer` on the two chain 401s. Request id: header on a 200, a valid
   client id echoed, an invalid one replaced.
7. `common/ApiExceptionHandlerTest` (`@WebMvcTest` with a throwaway controller that throws):
   the catch-all answers 500 `about:blank` with "Unexpected error" and no exception text;
   validation returns the `errors` list. Second slice test in the project.
   Commit: `test: error contract on both exits, request id`.

### Docs
8. README: error paragraph, the three example bodies, `X-Request-Id` line, link to
   `docs/errors.md`. Ladder rows Problem Details and request id to built. PROGRESS.
