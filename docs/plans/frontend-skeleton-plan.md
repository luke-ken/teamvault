# Feature plan: frontend skeleton with the generated API client

A Vite, React and TypeScript app in `frontend/`, a typed client generated from
`docs/openapi.yaml`, one real call through it (`GET /api/ping`), and the styling stack
(Tailwind CSS, shadcn/ui) with its first component. Decisions and trade-offs in
`../adr/008-api-client-generated-from-openapi.md` and `../adr/009-ui-tailwind-and-shadcn.md`;
this file is the build order. Method as in
the backend plans: inventory, forks settled before the first line, bottom-up, a test with each
step.

**Status:** planned 2026-11-15. Steps 1 and 2 built the same day (the skeleton commit), steps
3 and 4 on 2026-11-17 (the fix commit and the client commit); next is step 5, the styling
stack. As built: create-vite 9.3 scaffolds with oxlint already
configured and TypeScript 7.0, pinned down to 5.9.3. Prettier runs with `semi: false` and
`tabWidth: 4`, quotes and trailing commas on its defaults; `package-lock.json` is in
`.prettierignore` because npm rewrites it with its own indent. Vitest 4.1 without globals:
`test` and `expect` are imported, and the setup file registers Testing Library's cleanup.
Step 3's backend fix, decided 2026-11-17 with the output on screen: one customizer for every
schema whose name ends in `Response` (all properties required), not an annotation per field.
Six records and about twenty fields would carry the same annotation, and a new record would
forget it silently; the convention is guarded by a test instead. `ProblemDetail` declares
`status` and `title` as required, the members every error carries. The test also pins the
naming rule: every schema is a `*Request`, a `*Response` or `ProblemDetail`.
Step 4 as built: the client's base URL is `window.location.origin`, not empty. openapi-fetch
builds a `Request`, and Node's `Request` (which the tests run on) rejects a relative URL;
the origin is still the page's own, so fork 2 holds. Fork 6 verified: without the late
`fetch` lookup the two tests that stub `fetch` fail, because the client keeps the global it
saw at creation.

## Inventory

- Backend: 15 operations in `docs/openapi.yaml` (OpenAPI 3.1), diff-clean. Runs on `:8080`
  with `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. No CORS configuration, and
  none is planned. The refresh cookie is `SameSite=Strict; Path=/api/auth`, `Secure` off under
  `dev` only.
- Repo: `backend/`, `docs/`, no `frontend/`. Root `.gitignore` knows nothing about Node.
- Toolchain: Node 24, npm 11. Checked on npm 2026-11-15: vite 8.3, react 19.3,
  @vitejs/plugin-react 6.3, vitest 4.1 (5.x exists), openapi-typescript 7.13 (peer
  `typescript ^5.x`), openapi-fetch 0.17, oxlint 1.93, prettier 3.10, tailwindcss and @tailwindcss/vite 4.3 (peer
  range includes Vite 8), shadcn CLI 4.23, prettier-plugin-tailwindcss 0.8.
- Known coarse spot in the document: only the request schemas list `required` properties.
  Every response schema (`CompanyResponse`, `FileResponse`, `UserResponse`, ...) has none, so
  a generator types every response field as optional (`name?: string`). Seen in a trial run
  of openapi-generator on 2026-11-15; openapi-typescript reads the same document. The fix
  belongs in the backend's declarations (step 3).
- Not in this plan: router, TanStack Query, React Hook Form, zod (form
  input only), MSW, Playwright. Each arrives with
  the feature that needs it, so every dependency has a commit that explains it.

## Design forks, settled

1. **`frontend/` in this repository.** One clone, one pull request shows an API change next to
   the client change it forces, and the generator reads `../docs/openapi.yaml` by a relative
   path. Cost: CI needs path-aware jobs later. A second repository would need the document
   published somewhere first.
2. **One origin from the first day.** The Vite dev server proxies `/api` to
   `http://localhost:8080`. The browser only ever talks to `:5173`, so there is no CORS code,
   the cookie rules in dev match production, and the client's base URL is empty everywhere.
   Cookies ignore ports, so the refresh cookie set through the proxy comes back through it.
3. **TypeScript 5.9, not 6 or 7.** openapi-typescript drives the compiler API and declares
   `typescript ^5.x` as its peer. One compiler for app and generator; no `--legacy-peer-deps`,
   no overrides. Revisit when the peer range opens (ADR-008).
4. **Vitest 4** with jsdom and Testing Library: the set already proven with Vite 8 and React
   19. Vitest 5 can come in through Dependabot with a green suite as the gate.
5. **Generated file committed.** `src/api/schema.d.ts`, types only, written by
   `npm run api:generate`, listed in `.prettierignore` and in the oxlint ignore patterns. The
   drift check (`api:generate`, then `git diff --exit-code`) joins CI with the first workflow.
6. **The client reads `fetch` late.** openapi-fetch takes a `fetch` option and otherwise keeps
   the global it saw at creation. `client.ts` passes `(request) => globalThis.fetch(request)`
   so a test (and later MSW) can replace the global after the module has loaded. To verify in
   step 4: the first test fails without it or it does not, and the comment says which.
7. **Formatter and linter from the first commit** (ladder row): Prettier for format, oxlint
   for rules (`react`, `typescript`, `oxc` plugins, hooks rule as error). `npm run check` runs
   format check, lint, `tsc -b` and the tests in one go; that is what CI will call.
8. **Tailwind CSS 4 and shadcn/ui (ADR-009).** Tailwind through its Vite plugin, no PostCSS
   config and no `tailwind.config.js`: version 4 is configured in CSS (`@import "tailwindcss"`
   and `@theme` in `index.css`). shadcn/ui is not a dependency: its CLI copies component
   source into `src/components/ui/`, and from then on the files are ours, formatted and linted
   like the rest. It needs the `@/` path alias in `tsconfig` and `vite.config.ts`, and it
   writes a `components.json`. Components are added one at a time, each with the feature that
   first uses it; this plan adds `button` only. `prettier-plugin-tailwindcss` sorts the
   classes so diffs stay quiet. The CLI's prompts (base primitives, style, base colour) are
   read at the time and the answers noted here; nothing is assumed from older versions.
9. **Tests do not see styles.** jsdom does not run Tailwind, and the tests assert roles, labels
   and text, never classes. What a page looks like is checked by eye in dev, and later by the
   browser rung.

## Build order

### Skeleton
1. `npm create vite@latest frontend -- --template react-ts` in the repo root, then trim:
   remove the demo assets and counter, keep `main.tsx`, `App.tsx`, `index.css`. Set
   TypeScript to `~5.9`. Root `.gitignore`: nothing to add if the template's
   `frontend/.gitignore` covers `node_modules` and `dist` (check).
2. Tooling: oxlint with `.oxlintrc.json`, Prettier with `.prettierrc.json` and
   `.prettierignore`, Vitest with jsdom, Testing Library, `src/setupTests.ts`. Scripts: `dev`,
   `build` (`tsc -b && vite build`), `lint`, `format`, `format:check`, `test`, `check`.
   A first test: `App` renders its heading.
   Commit: `chore: frontend skeleton (Vite, React, TypeScript, Vitest, oxlint, Prettier)`.

### Client
3. Dependencies: `openapi-fetch` (runtime), `openapi-typescript` (dev). Script
   `api:generate`: `openapi-typescript ../docs/openapi.yaml -o src/api/schema.d.ts`. Run it,
   read the output once top to bottom: `paths`, `components`, `operations`, how the multipart
   body and the binary response came out. Anything coarse is fixed in the backend's
   declarations, never in this file (ADR-006). Expected first fix: response fields come out
   optional. Mark the response schemas' properties as required on the backend (one customizer
   for all response records, or `@Schema(requiredMode = REQUIRED)` per field; decide when the
   output is on screen), rerun the export test, regenerate. That is its own backend commit
   before this step's commit: `fix: response schemas declare their required properties`.
4. `src/api/client.ts`: `createClient<paths>({ baseUrl: '', fetch: ... })`, exported once.
   `vite.config.ts`: the `/api` proxy. `App.tsx`: calls `GET /api/ping` through the client in
   an effect and renders the three outcomes (waiting, answer, failure). Plain `useEffect` on
   purpose, for one commit: Session 3 replaces it with TanStack Query and the diff shows what
   the library takes over.
   Test: `App` shows the ping answer with `fetch` stubbed, and the failure text when the stub
   answers 500 with a Problem Details body.
   Commit: `feat: typed API client generated from the OpenAPI document (ADR-008)`.

### Styling
5. `tailwindcss` and `@tailwindcss/vite`, the plugin in `vite.config.ts`, `index.css` reduced
   to the import and the theme tokens. The `@/` alias. `npx shadcn@latest init`, then
   `npx shadcn@latest add button`; read the generated `button.tsx` and `lib/utils.ts` once
   (`cva` for the variants, `cn` for merging classes) before using them. `App.tsx`: a centred
   card with the ping answer and a "Ping again" button, built from utilities and the one
   component. `prettier-plugin-tailwindcss` in the Prettier config. The existing tests stay
   green unchanged, which is fork 9 holding.
   Commit: `chore: Tailwind CSS and shadcn/ui, first component (ADR-009)`.

### Docs
6. README: stack line, "Running it" gains the frontend half (two terminals, one URL), the
   regenerate command. Ladder: "Frontend client generated from the OpenAPI document" to
   built; "formatter enforced" row names Prettier and oxlint. ADR-008 and ADR-009 to accepted. Project
   `CLAUDE.md` structure line for `frontend/`.
   Commit: `docs: ADR-008 and ADR-009 accepted, frontend in the README and on the ladder`.

## Done when

- `npm run check` is green in `frontend/`.
- `npm run api:generate` leaves `git status` clean.
- Backend on `:8080`, `npm run dev`, the page on `:5173` shows the ping answer, and the
  browser's network tab shows the request going to `:5173/api/ping`.
