# TeamVault

Multi-tenant file upload & sharing service. Users belong to companies; a file uploaded
by one colleague is automatically visible to the whole company, and never to anyone
outside it. Storage tiers for small vs. large companies.

> **Status:** the backend core is running: tenant-isolated file upload/list/download on a
> hand-designed PostgreSQL schema, registration and bearer-token auth with self-issued JWTs,
> companies with an ADMIN/MEMBER role model enforced in the service layer, a health endpoint
> with readiness probes, integration-tested against a real Postgres (Testcontainers), formatter
> enforced by the build. This repo is built in public; the commit history *is* the
> build log. See [`PROGRESS.md`](PROGRESS.md) for the journal and
> [`docs/adr/`](docs/adr/) for architecture decisions.

## Design in three decisions

1. **Tenant isolation: shared schema + `company_id`**, enforced in the service layer and
   proven by a cross-tenant negative test. Why not schema- or database-per-tenant:
   [ADR-003](docs/adr/003-tenant-isolation-model.md).
2. **Schema designed by hand, owned by Flyway** (`V1__init.sql`), Hibernate only
   validates. Every index and constraint has a written justification:
   [docs/erd.md](docs/erd.md).
3. **Security on from day 1, deny-by-default** ([ADR-002](docs/adr/002-spring-security-from-day-one.md)),
   **authentication by short-lived self-issued JWT** ([ADR-004](docs/adr/004-self-issued-jwt-auth.md)):
   credentials leave the client exactly once, at login; the token carries identity only
   (`sub` = user id), never permissions. Authorization stays a per-request membership
   lookup in the service layer, so nothing in the token can go stale except the identity,
   and that window is the 15-minute TTL.

The API layer is stateless (no server-side session state), so it scales horizontally
without sticky sessions.

## Stack

- **Backend:** Java 21, Spring Boot, Spring Security (OAuth2 Resource Server for the
  self-issued HS256 JWT), Spring Data JPA, Flyway, PostgreSQL, local-FS blob store behind a
  single storage class (S3/MinIO is the designed swap)
- **Testing:** JUnit 5, MockMvc, Testcontainers (real Postgres per test run)
- **Ops:** Docker Compose (dev DB starts automatically with the app), Spring Boot Actuator
  (health only, probes on), Spring Java Format at the Maven validate phase, Dependabot
- **Production readiness:** what is built, what is planned per sprint and what is named
  only: [`docs/production-readiness.md`](docs/production-readiness.md)
- **Planned:** React + TypeScript frontend, Redis, GitHub Actions CI

## Running it

Requirements: JDK 21 and a running Docker daemon. The app and the tests are independent:
neither needs the other to be running.

### Configure the signing secret

The app refuses to boot without a JWT secret of at least 32 characters, so it can never
sign tokens with a known default key. Locally it is read from a gitignored `.env` next to
the `pom.xml`; CI and production set the variable directly.

```bash
cd backend
cp .env.example .env
# then replace the placeholder in .env with the output of:
openssl rand -base64 48
```

### Run the app

```bash
cd backend
./mvnw spring-boot:run
```

Spring Boot's Docker Compose support starts the dev Postgres from `compose.yaml` (port 5432)
before the app boots and stops it again when the app exits. The command stays in the
foreground, so use a second terminal for everything below.

### Try it

Dev seed (`V2__seed.sql`): two companies with disjoint members, password `devpass12`.

| User | Company | Role |
|---|---|---|
| `alice@acme.example` | Acme GmbH (`11111111-…-111111111111`) | ADMIN |
| `bob@globex.example` | Globex AG (`22222222-…-222222222222`) | MEMBER |

Every request except register, login, ping and health needs `Authorization: Bearer <token>`.
Log in once, keep the token in a shell variable (`jq` extracts it; any JSON tool works):

```bash
ACME=11111111-1111-1111-1111-111111111111

# register a new user (201, no token: tokens come from login only)
curl -s -X POST localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"carol@example.com","password":"correct-horse-battery","displayName":"Carol"}'
# -> {"id":"...","email":"carol@example.com","displayName":"Carol"}
# same email again, in any letter case: 409 Conflict

# log in as carol and found a company: the creator becomes its first ADMIN, same transaction
CAROL=$(curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"carol@example.com","password":"correct-horse-battery"}' | jq -r .token)
INITECH=$(curl -s -X POST localhost:8080/api/companies \
  -H "Authorization: Bearer $CAROL" -H "Content-Type: application/json" \
  -d '{"name":"Initech","tier":"SMALL"}' | jq -r .id)

# add bob as MEMBER (ADMIN only), then list the members
curl -s -X POST localhost:8080/api/companies/$INITECH/members \
  -H "Authorization: Bearer $CAROL" -H "Content-Type: application/json" \
  -d '{"email":"bob@globex.example"}'
curl -s -H "Authorization: Bearer $CAROL" localhost:8080/api/companies/$INITECH/members

# exchange credentials for a token, valid 15 minutes
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@acme.example","password":"devpass12"}' | jq -r .token)

# upload a file into Acme
curl -H "Authorization: Bearer $TOKEN" -F "file=@demo.txt" \
  localhost:8080/api/companies/$ACME/files

# list Acme's files (newest first)
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/companies/$ACME/files

# download
curl -H "Authorization: Bearer $TOKEN" \
  localhost:8080/api/companies/$ACME/files/<fileId>/download

# no token: 401 before any controller runs
curl -i localhost:8080/api/companies/$ACME/files
# -> WWW-Authenticate: Bearer
# -> X-Request-Id: 6f1c2a9e-...
# -> {"type":".../docs/errors.md#authentication-required","title":"Unauthorized","status":401,
#     "detail":"Authentication required","instance":"/api/companies/<acme>/files","requestId":"6f1c2a9e-..."}

# the point of the whole design: bob is not an Acme member
BOB=$(curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@globex.example","password":"devpass12"}' | jq -r .token)
curl -H "Authorization: Bearer $BOB" localhost:8080/api/companies/$ACME/files
# -> {"type":".../docs/errors.md#membership-required","title":"Forbidden","status":403,
#     "detail":"Not a member of this company","instance":"...","requestId":"..."}

# bob does see Initech under his companies, as MEMBER; adding someone himself is a 403
curl -s -H "Authorization: Bearer $BOB" localhost:8080/api/companies
curl -s -X POST localhost:8080/api/companies/$INITECH/members \
  -H "Authorization: Bearer $BOB" -H "Content-Type: application/json" \
  -d '{"email":"alice@acme.example"}'
# -> {"type":".../docs/errors.md#admin-role-required","title":"Forbidden","status":403,
#     "detail":"Requires the ADMIN role in this company","instance":"...","requestId":"..."}
```

A wrong password and an unknown email produce the same 401 body and take the same time
(the provider runs a dummy bcrypt check for unknown users), so login leaks nothing about
which emails exist. Registration does say "already registered": that is the usual trade,
sign-up needs the feedback, login must not give it.

Health is public and detail-free, the shape a readiness probe needs:

```bash
curl -s localhost:8080/actuator/health            # -> {"status":"UP"}, nothing more
curl -s localhost:8080/actuator/health/readiness  # the probe a platform would poll
curl -s -o /dev/null -w '%{http_code}' localhost:8080/actuator/env   # 404: not exposed
```

### Run the tests

```bash
cd backend
./mvnw test
```

The integration tests never touch the dev database. Testcontainers starts a throwaway
Postgres on a random port, Flyway migrates and seeds it, and the Ryuk sidecar removes
all test containers once the JVM exits. Only Docker needs to be up for this, not the app.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/ping` | public | smoke check |
| GET | `/actuator/health`, `/actuator/health/{liveness,readiness}` | public | UP or DOWN, no component details |
| POST | `/api/auth/register` | public | `{email, password, displayName}` in, `{id, email, displayName}` out, 409 on a known email |
| POST | `/api/auth/login` | public | `{email, password}` in, `{token, expiresAt}` out |
| POST | `/api/companies` | bearer | `{name, tier}` in, the caller becomes ADMIN in the same transaction |
| GET | `/api/companies` | bearer | the caller's companies with the caller's role in each |
| GET | `/api/companies/{companyId}` | bearer | company details (member only, 403 otherwise) |
| POST | `/api/companies/{companyId}/members` | bearer, ADMIN | `{email, role?}` in, 404 unknown email, 409 already a member |
| GET | `/api/companies/{companyId}/members` | bearer | members with roles (member only) |
| POST | `/api/companies/{companyId}/files` | bearer | multipart upload (member only) |
| GET | `/api/companies/{companyId}/files` | bearer | list company files, newest first |
| GET | `/api/companies/{companyId}/files/{fileId}` | bearer | file metadata |
| GET | `/api/companies/{companyId}/files/{fileId}/download` | bearer | file content |

Errors are Problem Details (RFC 9457, `application/problem+json`), never stack traces, on
both paths a request can fail: in the filter chain (401, missing or invalid token; the
security entry point hands the exception to the MVC resolver, so the same advice writes
every error body) and in the controllers (400 validation with an `errors` list of
`{field, message}`, 400 malformed body, 401 bad credentials, 403, 404, 409 including the
unique-constraint race behind the exists check). `type` is an anchor into the error
catalogue, [docs/errors.md](docs/errors.md), which clients switch on; `detail` is for
humans and may change. Every response, success included, carries `X-Request-Id` (a
well-formed client id is kept, anything else replaced), and the same id sits in the error
body and in each log line of that request (ADR-005).
Cross-tenant requests fail with 403 (not a member) or 404 (foreign file id under your
company), covered by `FileApiIntegrationTest.crossTenantAccess_isImpossible`. A non-member
gets 403 whether or not the company exists, so error codes never reveal which ids are real.
The role check sits on top of the membership check in one `MembershipGuard`: a member who
lacks ADMIN is told what is missing, a non-member is told nothing more than 403.

## Deliberately not built (yet)

Knowing what NOT to build at this scale is part of the design:

- **Tenant routing layer / tenant context service**: pointless below thousands of
  tenants; the Atlassian-style architecture this borrows from only earns its complexity
  at enterprise scale.
- **CQRS split, event-synced read replicas, multi-region**: same reasoning.
- **Distributed caching + invalidation broadcast**: no read-path bottleneck exists to
  justify it; if caching enters, invalidation is the problem to design first.
- **Refresh tokens and token revocation**: a 15-minute access token without refresh keeps
  the server stateless; the client logs in again. The triggers that would change this
  (a second service verifying tokens, an external identity provider) are written down in
  [ADR-004](docs/adr/004-self-issued-jwt-auth.md).
- **Role changes, member removal, company update and delete**: the read and create
  paths exist; the destructive ones wait for the audit trail in Sprint 4, so nothing
  disappears without a record of who did it.
- **Everything on the production ladder marked planned or named**: rate limiting, Redis,
  object store, CI, metrics. Each with its sprint or its reason in
  [`docs/production-readiness.md`](docs/production-readiness.md).
