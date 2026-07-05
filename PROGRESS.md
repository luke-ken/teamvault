# TeamVault — progress

Newest on top. Format: `YYYY-MM-DD — sprint/session — done / next`.

- 2026-07-05 — S1·S2 — First green run. Spring Boot 4.1/Java 21 skeleton in `backend/`
  (Web, JPA, Flyway, Security, Validation, Actuator, Testcontainers). `GET /api/ping` → pong,
  everything else deny-by-default (ADR-002). No datasource in yaml: docker-compose starts
  dev Postgres, Testcontainers covers tests (ADR-001). `./mvnw test` green (2/2), live run
  verified end-to-end. Gotcha: Boot 4 moved `@WebMvcTest` to `o.s.boot.webmvc.test.autoconfigure`.
  **Next:** Session 3 (T) — relational modeling theory, then Session 4 — ERD.
- 2026-07-04 — setup — Repo is live: git init + first push to `luke-ken/teamvault` (public).
  README stub with pitch/stack/status, ADR template, .gitignore. Building in public from here.
  **Next:** Sprint 1 · Session 2 — Spring Initializr, Postgres in Docker, `/ping` green run.
- 2026-06-07 — setup — Project folder + CLAUDE.md created. Not yet a git repo / no code.
