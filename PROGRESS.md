# TeamVault progress

Newest on top. Format: `YYYY-MM-DD – sprint/session – done / next`.

- 2026-08-17 – S1·S3–S8 – The big push. ADR-003 (tenant isolation: shared schema +
  company_id, service-layer enforcement, RLS named as hardening path), ERD hand-sketched
  then captured in `docs/erd.md` with per-index justifications, `V1__init.sql` (4 tables,
  tenant-scoped uniques, company_id-first composite indexes) + `V2__seed.sql` (two
  disjoint tenants, deterministic UUIDs). JPA entities + repositories (self-written,
  validated against the schema via ddl-auto=validate). Interim auth: HTTP Basic over
  bcrypt `app_user` rows, deny-by-default kept per ADR-002, JWT stays the named swap.
  File feature: multipart upload → local-FS blob store (single swap-point class) +
  metadata row, tenant-scoped list/download, clean JSON errors without stack traces.
  `FileApiIntegrationTest`: round-trip, duplicate-filename 409, 401, and the
  cross-tenant negative test (403 as non-member, 404 for a foreign file id under your
  own company). 6/6 green against real Postgres (Testcontainers); live curl round-trip
  verified. README rewritten: design-in-three-decisions, runnable curl examples,
  "deliberately not built" list. **Next:** company/user CRUD + registration, JWT swap,
  strip the scaffold TODO comments from entities/repositories.
- 2026-07-05 – S1·S2 – First green run. Spring Boot 4.1/Java 21 skeleton in `backend/`
  (Web, JPA, Flyway, Security, Validation, Actuator, Testcontainers). `GET /api/ping` → pong,
  everything else deny-by-default (ADR-002). No datasource in yaml: docker-compose starts
  dev Postgres, Testcontainers covers tests (ADR-001). `./mvnw test` green (2/2), live run
  verified end-to-end. Gotcha: Boot 4 moved `@WebMvcTest` to `o.s.boot.webmvc.test.autoconfigure`.
  **Next:** Session 3 (T), relational modeling theory; then Session 4, the ERD.
- 2026-07-04 – setup – Repo is live: git init + first push to `luke-ken/teamvault` (public).
  README stub with pitch/stack/status, ADR template, .gitignore. Building in public from here.
  **Next:** Sprint 1 · Session 2: Spring Initializr, Postgres in Docker, `/ping` green run.
- 2026-06-07 – setup – Project folder + CLAUDE.md created. Not yet a git repo / no code.
