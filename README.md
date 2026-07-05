# TeamVault

Multi-tenant file upload & sharing service. Users belong to companies; a file uploaded
by one colleague is automatically visible to the whole company, with storage tiers for
small vs. large companies.

> **Status:** early setup — backend scaffolding is next. This repo is built in public;
> the commit history *is* the build log. See [`PROGRESS.md`](PROGRESS.md) for the
> session-by-session journal and [`docs/adr/`](docs/adr/) for architecture decisions.

## Stack

- **Backend:** Java 21, Spring Boot, Spring Security (JWT), Spring Data JPA, Flyway,
  PostgreSQL, MinIO/S3 for blob storage (local FS first), Redis (caching, later phase)
- **Frontend:** React + Vite + TypeScript, TanStack Query, React Hook Form
- **Testing:** JUnit 5, Mockito, Testcontainers; MSW + Cypress on the frontend
- **Ops:** Docker Compose, GitHub Actions, Spring Actuator + Prometheus/Grafana

## Running it

_Coming with the first backend milestone — the goal is a single `docker compose up`._
