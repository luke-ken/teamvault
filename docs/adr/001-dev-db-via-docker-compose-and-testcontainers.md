# ADR-001: Dev database via spring-boot-docker-compose, tests via Testcontainers

- **Date:** 2026-07-05
- **Status:** accepted

## Context
The app needs PostgreSQL locally (dev) and in tests, with as little manual setup and
config drift as possible. The naive approach is a hand-run `docker run postgres` plus a
hardcoded datasource URL in `application.yaml` — easy to explain, but easy to forget to
start, and the credentials live in two places (compose/docker command *and* the yaml).

## Options considered
1. **Manual `docker run` + hardcoded `spring.datasource.*`** — simplest mental model;
   but the container lifecycle is manual, and config drifts between the run command and yaml.
2. **`spring-boot-docker-compose` (dev) + Testcontainers (tests)** — Spring starts/stops the
   `compose.yaml` Postgres on app start and injects the connection details; tests spin up a
   throwaway Postgres per run. Zero datasource config in yaml; one source of truth (`compose.yaml`).
   Cost: more "magic" to understand, and requires Docker running for both dev and tests.

## Decision
Option 2. `application.yaml` deliberately contains **no datasource URL**; real environments
will supply `SPRING_DATASOURCE_*` via environment variables.

## Trade-off (why this, what we give up)
We accept hidden convention over explicit config: someone reading `application.yaml` can't
see where the DB comes from (hence the comment there). We also hard-require Docker for any
local run or test. In exchange, `./mvnw spring-boot:run` and `./mvnw test` work from a fresh
clone with zero setup steps, and dev/test never share state. Revisit if CI has no Docker,
or when a teammate without Docker needs to run against an external DB.
