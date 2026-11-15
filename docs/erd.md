# TeamVault ERD

Hand-designed first, then captured here. Source of truth for the physical schema is the
Flyway migrations (`backend/src/main/resources/db/migration/`, `V1__init.sql` for the four
tenant tables, `V3__refresh_token.sql` for the token table); this diagram is the readable map. Tenant-isolation reasoning lives in
[ADR-003](adr/003-tenant-isolation-model.md).

![TeamVault entity-relationship diagram](erd.svg)

Crow's foot notation: double tick = exactly one, circle + crow's foot = zero or many.

<details>
<summary>Mermaid source (editable mirror of the diagram)</summary>

```mermaid
erDiagram
    company ||--o{ membership : has
    company ||--o{ file_metadata : owns
    membership }o--|| app_user : "belongs to"
    file_metadata }o--|| app_user : "uploaded by"
    refresh_token }o--|| app_user : "issued to"

    company {
        uuid id PK
        text name
        text tier "SMALL | LARGE"
        timestamptz created_at
    }
    app_user {
        uuid id PK
        text email UK
        text password_hash
        text display_name
        timestamptz created_at
    }
    membership {
        uuid id PK
        uuid user_id FK
        uuid company_id FK
        text role "MEMBER | ADMIN"
        timestamptz created_at
    }
    file_metadata {
        uuid id PK
        uuid company_id FK
        uuid uploaded_by FK
        text filename
        text content_type
        bigint size_bytes
        text storage_key
        timestamptz created_at
    }
    refresh_token {
        uuid id PK
        uuid user_id FK
        uuid family_id
        text token_hash UK
        timestamptz expires_at
        timestamptz family_expires_at
        timestamptz used_at
        timestamptz revoked_at
        timestamptz created_at
    }
```

</details>

## Constraints and indexes (the deliberate ones)

| Where | What | Why |
|---|---|---|
| `membership` | `UNIQUE (company_id, user_id)` | one membership per user per company; leading with `company_id`, its backing index also serves "members of company X" |
| `membership` | `INDEX (user_id)` | the reverse path, "companies of user Y", runs on every authenticated request; a composite index cannot serve both directions (leftmost prefix rule) |
| `file_metadata` | `UNIQUE (company_id, filename)` | filenames are unique per tenant, not globally |
| `file_metadata` | `UNIQUE (storage_key)` | exactly one metadata row per stored blob; duplicate keys would make blob deletion unsafe |
| `file_metadata` | `INDEX (company_id, created_at)` | the main listing query, "a company's files, newest first", is a range scan over that tenant's slice |
| `app_user` | `UNIQUE (email)` | login identity; globally unique because users exist independently of companies |
| `refresh_token` | `UNIQUE (token_hash)` | the lookup on every refresh is by hash; unique because two rows for one token would make "was this token used" ambiguous |
| `refresh_token` | `INDEX (family_id)` | revocation touches the family's live rows, a replayed token revokes a whole family at once |
| `refresh_token` | `INDEX (expires_at)` | the nightly sweep deletes by expiry alone; without the index it would scan every row ever issued |

Every composite index on tenant-owned data leads with `company_id`: every access path
starts at the tenant boundary (ADR-003). `refresh_token` is not tenant data: it hangs off
the user, who exists independently of companies, and is reached by hash, never by tenant.
`family_expires_at` is copied from the first row of a login to every successor, so a row's
`expires_at` is already the smaller of the idle and the family limit ([ADR-007](adr/007-refresh-token-rotation.md)).
