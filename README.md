# TeamVault

Multi-tenant file upload & sharing service. Users belong to companies; a file uploaded
by one colleague is automatically visible to the whole company, and never to anyone
outside it. Storage tiers for small vs. large companies.

> **Status:** the backend core is running: tenant-isolated file upload/list/download on a
> hand-designed PostgreSQL schema, HTTP Basic auth, integration-tested against a real
> Postgres (Testcontainers). This repo is built in public; the commit history *is* the
> build log. See [`PROGRESS.md`](PROGRESS.md) for the journal and
> [`docs/adr/`](docs/adr/) for architecture decisions.

## Design in three decisions

1. **Tenant isolation: shared schema + `company_id`**, enforced in the service layer and
   proven by a cross-tenant negative test. Why not schema- or database-per-tenant:
   [ADR-003](docs/adr/003-tenant-isolation-model.md).
2. **Schema designed by hand, owned by Flyway** (`V1__init.sql`), Hibernate only
   validates. Every index and constraint has a written justification:
   [docs/erd.md](docs/erd.md).
3. **Security on from day 1, deny-by-default** ([ADR-002](docs/adr/002-spring-security-from-day-one.md));
   HTTP Basic against bcrypt-hashed users as the interim mechanism, JWT is the named swap.

The API layer is stateless (no server-side session state), so it scales horizontally
without sticky sessions.

## Stack

- **Backend:** Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway, PostgreSQL,
  local-FS blob store behind a single storage class (S3/MinIO is the designed swap)
- **Testing:** JUnit 5, MockMvc, Testcontainers (real Postgres per test run)
- **Ops:** Docker Compose (dev DB starts automatically with the app)
- **Planned:** JWT auth, React + TypeScript frontend, Redis, GitHub Actions CI

## Running it

Requirements: JDK 21, Docker running.

```bash
cd backend
./mvnw spring-boot:run   # starts dev Postgres via docker compose automatically
./mvnw test              # integration tests against a throwaway Postgres
```

Dev seed (`V2__seed.sql`): two companies with disjoint members, password `devpass12`.

| User | Company | Role |
|---|---|---|
| `alice@acme.example` | Acme GmbH (`11111111-…-111111111111`) | ADMIN |
| `bob@globex.example` | Globex AG (`22222222-…-222222222222`) | MEMBER |

```bash
ACME=11111111-1111-1111-1111-111111111111

# upload a file into Acme
curl -u alice@acme.example:devpass12 -F "file=@demo.txt" \
  localhost:8080/api/companies/$ACME/files

# list Acme's files (newest first)
curl -u alice@acme.example:devpass12 localhost:8080/api/companies/$ACME/files

# download
curl -u alice@acme.example:devpass12 \
  localhost:8080/api/companies/$ACME/files/<fileId>/download

# the point of the whole design: bob is not an Acme member
curl -u bob@globex.example:devpass12 localhost:8080/api/companies/$ACME/files
# -> {"status":403,"error":"Forbidden","message":"Not a member of this company"}
```

## API

| Method | Path | Description |
|---|---|---|
| GET | `/api/ping` | health check (public) |
| POST | `/api/companies/{companyId}/files` | multipart upload (member only) |
| GET | `/api/companies/{companyId}/files` | list company files, newest first |
| GET | `/api/companies/{companyId}/files/{fileId}` | file metadata |
| GET | `/api/companies/{companyId}/files/{fileId}/download` | file content |

Errors are clean JSON (`{status, error, message}`), never stack traces. Cross-tenant
requests fail with 403 (not a member) or 404 (foreign file id under your company),
covered by `FileApiIntegrationTest.crossTenantAccess_isImpossible`.

## Deliberately not built (yet)

Knowing what NOT to build at this scale is part of the design:

- **Tenant routing layer / tenant context service**: pointless below thousands of
  tenants; the Atlassian-style architecture this borrows from only earns its complexity
  at enterprise scale.
- **CQRS split, event-synced read replicas, multi-region**: same reasoning.
- **Distributed caching + invalidation broadcast**: no read-path bottleneck exists to
  justify it; if caching enters, invalidation is the problem to design first.
- **Roles beyond membership, user/company CRUD endpoints, JWT**: next on the roadmap;
  the schema and auth foundation for them are already in place.
