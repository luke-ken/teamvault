# ADR-008: Frontend API client as generated types plus a thin fetch wrapper

- **Date:** 2026-11-15
- **Status:** proposed

## Context
The React frontend starts in Sprint 3. ADR-006 committed the API contract as
`docs/openapi.yaml` so that the frontend can generate from a file instead of mirroring records
by hand. The document has 15 operations, one multipart upload, one binary download, a bearer
scheme, and one error shape (`ProblemDetail` with `requestId` and `errors`). The client has
three jobs beyond typing: attach the access token, refresh once on a 401 and retry
(ADR-007), and let the UI read error bodies as typed Problem Details. Server state in the UI
is TanStack Query. The question is how much of the client is generated: types only, types
and calls, or types, calls and query hooks.

## Options considered
1. **Hand-written types and fetch calls**: no tool, full control. Cons: this is the drift
   ADR-006 was written to prevent; a renamed field shows up as `undefined` at runtime.
2. **openapi-typescript plus openapi-fetch**: the generator emits one `schema.d.ts` with types
   only, no runtime code. openapi-fetch is a small wrapper around `fetch` whose paths, params,
   bodies and responses are checked against those types, with a middleware hook for the bearer
   and the 401 retry. Pros: the generated file is readable and has nothing to debug; a contract
   change breaks the build at the call site; errors come back as a typed value, not an
   exception. Cons: query hooks, keys and invalidation are written by hand; multipart and blob
   need one explicit option each; the generator's peer range is TypeScript 5, which pins the
   compiler for the whole frontend below the current major. Maintenance signal, checked on npm
   on 2026-11-15: the last release (7.13.0) is from February 2026, before TypeScript 6 shipped,
   and nothing has followed in nine months. orval and openapi-generator both released within
   the last week.
3. **orval**: generates functions and the TanStack Query hooks, optionally MSW handlers. Pros:
   least code to write, hooks exist the moment an endpoint does. Cons: a large generated
   surface with its own key scheme and options to learn; invalidation is still by hand, now
   against keys someone else named; custom auth goes through a mutator file; more generated
   code in review.
4. **openapi-generator (typescript-fetch)**: the classic, and the usual pick where one tool
   has to serve several languages and server stubs. Pros: broad community, steady releases,
   the JVM it needs is already here for the backend, middleware hooks for the bearer and the
   retry exist here too. Tried against this document on 2026-11-15 (generator 7.27.0): the
   3.1 document went through with a warning that the tool's OpenAPI 3.1 support is still in
   beta, multipart upload and blob download work without extra options,
   and `date-time` fields arrive as `Date` objects. Cons, from the same run: it generates
   runtime code and not only types, 23 TypeScript files and about 2,650 lines for 15
   operations (an API class per tag, a model file with JSON mappers per schema, a 570-line
   runtime), so a contract change shows up as a diff across several generated files, and that
   code is ours to read when something misbehaves. A failed request throws a `ResponseError`
   that carries the raw response; reading it as Problem Details is a step we would write
   ourselves.

## Decision
Option 2. `npm run api:generate` runs openapi-typescript on `../docs/openapi.yaml` and writes
`frontend/src/api/schema.d.ts`. The file is committed, never edited, and excluded from
formatter and linter. One module creates the openapi-fetch client with an empty base URL,
because SPA and API share one origin in every environment (Vite proxy in dev, the reverse
proxy later). Query hooks are written by hand per feature, on top of the typed client. A CI
step regenerates and fails on a diff, the frontend twin of the export check from ADR-006.

## Trade-off (why this, what we give up)
We write the hooks ourselves: for 15 operations that is a few dozen lines per feature. The
reason is not the line count. How long a file list may be stale and which mutation
invalidates which query are domain decisions that differ per application; a generator can
only guess them, so they belong in code we wrote and can explain. We give up generated MSW
handlers; the handlers are typed against the same schema by hand, so a contract change still
breaks them at compile time. We accept TypeScript 5.9 for the frontend until
openapi-typescript's peer range opens to newer majors; the alternative, forcing the install
past the peer range, would put an unsupported compiler under the one tool whose output must
be exact. We accept a generator that has been quiet for nine months, because the exit is
cheap: its output is one file of types with no runtime, so replacing it means regenerating
that file with another tool and adapting one client module, not the features. Generated types
are a compile-time promise only: a response that does not match the document passes through
unchecked at runtime. We accept that, because client and API are built from one repository,
deployed together, and tied by two drift checks; hand-written runtime schemas would be the
second source of truth this decision exists to avoid. Runtime validation earns its place at
a boundary we do not control (a third-party API, a separately versioned backend), and then
the schemas should be generated from the same document. We accept a
second committed generated file with the same staleness risk as the first, closed the same
way, by a check in CI. Revisit if openapi-typescript has no release supporting a current
TypeScript major by the end of Sprint 4, if the API grows to the point where hand-written
hooks are mostly repetition (a rough line: more than 40 operations), or if a second client in
another language appears: then a generator with a broader output earns its surface.
