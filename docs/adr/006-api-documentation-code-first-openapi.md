# ADR-006: API documentation as a code-first OpenAPI document, committed, UI in dev only

- **Date:** 2026-10-23
- **Status:** accepted (2026-10-25)

## Context
The frontend arrives in Sprint 3 and needs to know the API: paths, bodies, the bearer scheme,
and the Problem Details shape from ADR-005. Today that knowledge lives in the controllers, the
records and the README. Hand-written TypeScript interfaces would mirror it and drift silently;
the first symptom of a renamed field would be an `undefined` at runtime. An OpenAPI document is
the machine-readable form that generators and viewers read instead of humans reading code.
Two questions follow: who writes the document, and who may see it.

## Options considered
1. **Contract-first**: write `openapi.yaml` by hand, generate server stubs and the client from it.
   Pros: the document is reviewed before any code exists; teams can build in parallel; nothing
   in the code can leave the document stale. Cons: every change is edited twice (document and
   implementation); generated server stubs fight with hand-written Spring code; for one author
   with a backend that already exists, pure overhead.
2. **Code-first with springdoc, document served only**: springdoc derives the document from the
   controllers at runtime and serves it on `/v3/api-docs`. Pros: zero duplication, always in
   sync with the running code. Cons: the document exists only while the app runs; a review
   cannot see what an API change did; the frontend build needs a running backend to generate.
3. **Code-first with springdoc, document exported and committed**: as option 2, plus a test
   fetches the generated document and writes it to `docs/openapi.yaml`. Pros: API changes show
   up as a diff in the pull request; the frontend generates from a file; nothing new to run.
   Cons: a generated file in the repository; the test writes outside `target`, which is unusual;
   a stale file is only caught once CI compares it (Sprint 3).
4. **Swagger UI everywhere** vs **UI in dev only**: the UI is convenient and harmless in
   itself, but a production API does not need to advertise and exercise itself from a browser
   page, and the UI is one more surface to patch. The document is the contract and stays
   public; the UI is a developer tool.

## Decision
Option 3 with the UI restricted: springdoc 3.x (the Boot 4 line), one `OpenAPI` bean declaring
the info, a fixed server and the bearer scheme as a global requirement, cleared on the two public
controllers. Error responses are declared once through a customizer with a hand-written
`ProblemDetail` schema that includes `requestId` and `errors`, because Spring's class has no
fields for the extension members and reflection cannot find them. A test exports
`/v3/api-docs.yaml` to `docs/openapi.yaml`, ordered by keys so two runs give one file. Swagger
UI is on only under the `dev` profile; `/v3/api-docs` stays public in every profile.

## Trade-off (why this, what we give up)
We accept a generated file under version control and a test with a side effect, in exchange
for API changes that are visible in review and a frontend that generates its client from a file
in the same repository. We give up the contract-first guarantee that nothing ships undocumented;
in return the document can never disagree with the code, only be stale, and staleness is a CI
check away (Sprint 3). Precision is bounded by what the code declares: where a generated type is
too coarse, the fix is a schema annotation or a declared schema, never an edit of generated
code. Revisit if a second team or a public client appears: then the document becomes a reviewed
contract first and contract-first earns its cost.
