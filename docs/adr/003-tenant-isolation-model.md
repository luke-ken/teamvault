# ADR-003: Shared schema with company_id as the tenant isolation model

- **Date:** 2026-08-16
- **Status:** accepted

## Context
TeamVault lets users upload documents. A user belongs to a company, and the company is the
tenant: multiple tenants exist side by side, each with its own users. A user from company A
may only see documents that belong to company A. Documents from other companies must be
invisible, enforced by the backend.

Every table and every query from here on depends on how tenants are isolated, and the
choice is expensive to reverse, so it is decided up front.

## Options considered
1. **Database per tenant**: strongest physical isolation; per-tenant backup, restore, and
   deletion are trivial. This is the model Atlassian migrated Jira/Confluence to.
   _Rejected:_ the operational overhead is enormous. Every migration runs against N
   databases, N connection pools need managing, and provisioning a new tenant becomes an
   infrastructure task instead of an `INSERT`. Cost and complexity scale with tenant
   count. Worth it for enterprise SaaS in regulated fields where physical isolation is a
   contractual or legal requirement; clearly out of scope for this app.
2. **Schema per tenant**: a middle ground where each tenant gets its own schema inside a
   mostly shared database. _Rejected:_ less overhead than option 1, but the real costs
   remain: migrations still run N times and need orchestration, every request must be
   routed to its tenant's schema (Hibernate supports this via its multi-tenancy SPI, but
   it is machinery to own), and the catalog bloats at high tenant counts. Worth it when
   tenants need some data-level separation but a database each is too expensive.
   TeamVault serves identical tenants with uniform data.
3. **Shared schema + `company_id` column on every tenant-owned table**: weakest physical
   isolation. All tenants share one database and one schema, and isolation is enforced by
   the application at the query level. Its strength is minimal overhead: one schema, one
   migration path, one connection pool, and tenant provisioning is a single `INSERT`.
   Its weakness is that isolation is only as strong as the application code: a single
   missed filter leaks data. Worth it for homogeneous tenants with no contractual
   isolation requirement, which is exactly this app.

## Decision
**Option 3.** A shared schema with a `company_id` column on every tenant-owned table.

**What is tenant-owned:** `file_metadata` (and every future table holding tenant data).
`app_user` is deliberately **not** tenant-owned: a user may belong to several companies,
so the user-to-company relation lives in a `membership` join table (`user_id` FK,
`company_id` FK, `UNIQUE(user_id, company_id)`), and the caller's membership decides
which tenant's data a request may touch.

**Enforcement rule:** every query on tenant-owned data is scoped by `company_id`, no
exceptions. The guarantee lives in the service layer: the scope is derived from the
authenticated caller's membership, never taken raw from the client. It is proven by a
negative test: a user of company A requesting a file of company B must get a
not-found/denied response.

**Defense in depth (named, not built):** PostgreSQL Row Level Security could add a
second, database-level enforcement layer under the service-layer rule. Not enabled at
current scale; noted as the hardening path if the isolation guarantee ever needs to
survive application bugs.

**Index consequence:** composite indexes on tenant-owned tables lead with `company_id`,
for example `(company_id, filename)` and `(company_id, created_at)`. Indexes do not
enforce isolation (the service-layer `WHERE company_id = ?` does); they make
tenant-scoped queries fast. Because every access path starts at the tenant boundary, an
index that does not lead with `company_id` cannot serve those lookups at all (leftmost
prefix rule); leading with it turns every tenant query into a range scan over that
tenant's slice of the table. Uniqueness follows the same shape: business keys are unique
per tenant (`UNIQUE(company_id, filename)`), not globally.

**Upgrade path:** options 1 and 2 stay reachable because `company_id` is a ready-made
partition key. The move is still a real per-tenant data migration (export per tenant,
dual-write or downtime), not a cheap one. Option 2 becomes worth it when a tenant needs
schema customization or per-tenant backup/restore; option 1 when a tenant contractually
requires physical isolation. A hybrid is possible: a few large tenants on dedicated
databases while the rest share one.

## Trade-off (why this, what we give up)
Gained:
- One schema, one migration path, near-zero operational overhead.
- Reversibility asymmetry: the `company_id` column keeps options 1 and 2 reachable,
  whereas starting with either of them would make consolidating back to a shared schema
  the hard migration.

Given up:
- Isolation depends on code correctness, mitigated by central service-layer enforcement
  plus the cross-tenant negative test rather than per-query discipline.
- No per-tenant backup or restore.
- Noisy-neighbor risk: one tenant's heavy read/write load affects all others.
- Slightly larger composite indexes and tenant-scoped uniqueness constraints to maintain.
