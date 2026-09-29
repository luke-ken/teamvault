# TeamVault

Multi-tenant file upload & sharing service. Users belong to companies; a file uploaded
by one colleague is automatically visible to the whole company, and never to anyone
outside it. Storage tiers for small vs. large companies.

> **Status:** the backend core is running: tenant-isolated file upload/list/download on a
> hand-designed PostgreSQL schema, bearer-token auth with self-issued JWTs, integration-tested
> against a real Postgres (Testcontainers). This repo is built in public; the commit history *is* the
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
- **Ops:** Docker Compose (dev DB starts automatically with the app)
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

Every request except login and ping needs `Authorization: Bearer <token>`. Log in once,
keep the token in a shell variable (`jq` extracts it; any JSON tool works):

```bash
ACME=11111111-1111-1111-1111-111111111111

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
# -> {"status":401,"error":"Unauthorized","message":"Authentication required"}

# the point of the whole design: bob is not an Acme member
BOB=$(curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@globex.example","password":"devpass12"}' | jq -r .token)
curl -H "Authorization: Bearer $BOB" localhost:8080/api/companies/$ACME/files
# -> {"status":403,"error":"Forbidden","message":"Not a member of this company"}
```

A wrong password and an unknown email produce the same 401 body and take the same time
(the provider runs a dummy bcrypt check for unknown users), so login leaks nothing about
which emails exist.

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
| GET | `/api/ping` | public | health check |
| POST | `/api/auth/login` | public | `{email, password}` in, `{token, expiresAt}` out |
| POST | `/api/companies/{companyId}/files` | bearer | multipart upload (member only) |
| GET | `/api/companies/{companyId}/files` | bearer | list company files, newest first |
| GET | `/api/companies/{companyId}/files/{fileId}` | bearer | file metadata |
| GET | `/api/companies/{companyId}/files/{fileId}/download` | bearer | file content |

Errors are clean JSON (`{status, error, message}`), never stack traces, on both paths a
request can fail: in the filter chain (401, missing or invalid token, written by a custom
entry point) and in the controllers (400 validation, 401 bad credentials, 403, 404).
Cross-tenant requests fail with 403 (not a member) or 404 (foreign file id under your
company), covered by `FileApiIntegrationTest.crossTenantAccess_isImpossible`.

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
- **Roles beyond membership, user/company CRUD endpoints**: next on the roadmap; the
  schema and auth foundation for them are already in place.
