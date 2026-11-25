# Feature plan: login, session and protected routes in the SPA

The frontend half of ADR-007: the access token in memory, a silent refresh on load, a refresh
on 401 with one retry, login and register forms, a route guard, logout. Decisions and
trade-offs in `../adr/007-refresh-token-rotation.md` and
`../adr/008-api-client-generated-from-openapi.md`; this file is the build order. Method as in
the other plans: inventory, forks settled before the first line, bottom-up, a test with each
step.

**Status:** accepted 2026-11-22. Steps 1 and 2 committed 2026-11-25. As built: msw 2.15 (see
the inventory), the session has a fourth status `unreachable`, and a call sent without a
token is neither cloned nor retried. Step 3 as built: react-router 8.5 in declarative mode
needed no change to this plan; the two guards are `RequireSession` and `AnonymousOnly`, and
the cache rule of fork 6 is one function, `clearCacheWithoutSession`, shared by `main.tsx`
and the test helper. Found on the way: `strict` was off in both tsconfig files (the
template relies on the TypeScript 6 default, this project pins 5.9), noticed through a
missing null check on the router's location state. It is on now; the code needed no change.

## Inventory

- API (`docs/openapi.yaml`), all four auth operations declare `security: []`:
  - `POST /api/auth/login`: `LoginRequest` (email, password), 200 `TokenResponse`
    (`token`, `expiresAt`) and the refresh cookie. 401 `bad-credentials`.
  - `POST /api/auth/register`: `RegisterRequest` (email up to 254, password 8 to 72,
    displayName up to 100, none blank), 201 `UserResponse`. No token and no cookie: a
    registration is not a login. 400 `validation-failed` with `errors[]`, 409 `already-exists`.
  - `POST /api/auth/refresh`: cookie in, 200 `TokenResponse` and the successor cookie. 401
    `invalid-refresh-token`, one body for every reason.
  - `POST /api/auth/logout`: 200, revokes the family, clears the cookie.
  - `GET /api/me`: `UserResponse`, needs the bearer.
- Cookie: `HttpOnly; SameSite=Strict; Path=/api/auth`, `Secure` off under the `dev` profile
  only. The page never sees it; the browser attaches it to the two calls under that path.
- Server rule the frontend must respect: a refresh token works once. A second use revokes
  the family. The server cannot tell an attacker from a careless client.
- Frontend today: `src/api/client.ts` (openapi-fetch, no middleware), `ApiError`,
  `createQueryClient()` (30 s `staleTime`, no retry on a 4xx), `renderApp`, the
  ping card as the only screen, tests that stub `fetch` by hand. No router, no forms.
- openapi-fetch 0.17 middleware: `onRequest({ request, schemaPath, id })`,
  `onResponse({ request, response, schemaPath, id })`, `onError`. `onResponse` may return a
  different `Response`. `id` is unique per call and the same in all three hooks.
- Checked on npm 2026-11-22: react-router 8.5, react-hook-form 7.92, zod 4.6,
  @hookform/resolvers 7.0 (peers include zod 4 and react-hook-form 7.55+), msw 3.0.
  **msw stays on 2.15:** Vitest 4.1 declares `msw ^2.4.9` as an optional peer (through
  `@vitest/mocker`), and msw 3 leaves `npm ls` with an invalid tree. Vitest 5 accepts both,
  so msw 3 comes in together with it. react-router 8 is a new major for this project: its
  API is read at install time and the as-built notes record what differs from this plan.
- Not in this plan: the cross-tab lock and channel (Session 6), company and file screens
  (Session 7), handlers typed against the generated schema (Session 8), the browser rung
  (Session 9), a proactive refresh before `expiresAt` (named, not built).

## Design forks, settled

1. **The session lives in a module, not in React.** `src/auth/session.ts` holds
   `{ status, token }` with status `loading`, `authenticated`, `anonymous` or `unreachable`, plus
   `subscribe`. Components read it through `useSyncExternalStore` in a `useSession()` hook.
   Reason: the middleware needs the token outside any component, and the refresh must not
   run inside an effect. StrictMode mounts twice; a second refresh would send the same
   cookie, and the server would revoke the family. No context provider: there is no state
   left for one to own. Cost: module state survives between tests, so the test setup resets
   it after each test.
2. **One refresh function, one promise.** `refreshSession(failedToken?)` is the only caller
   of `POST /api/auth/refresh`. While a call is pending, every further caller gets the same
   promise. Its first line asks whether the store already holds a token other than the one
   that failed; if so, it returns that token without a call. Inside one tab the shared
   promise already covers this; the check is there for Session 6, where the lock goes around
   this function and a second tab may have delivered the token.
3. **Boot before the first render decision.** `main.tsx` calls `refreshSession()` once,
   outside the component tree. Only a 401 is a verdict (`anonymous`). No answer or a 5xx
   leaves a running session alone (the caller gets its 401, the next call tries again); on
   load nobody knows whether a session exists, so the status ends `unreachable` and a page
   with a retry button shows, not a login form that would fail as well. The
   guard renders nothing while the status is `loading`, so a reload never flashes the login
   page.
4. **The middleware switches on `schemaPath`.** Paths under `/api/auth/` get no bearer and
   never trigger a refresh: the 401 of login means a wrong password, the 401 of refresh
   means no session. Every other call gets `Authorization: Bearer` in `onRequest`.
5. **Retry once, with a clone.** A request body can be read only once, so `onRequest` keeps
   `request.clone()` in a `Map` under the call's `id`; `onResponse` and `onError` remove it.
   On a 401, `onResponse` awaits `refreshSession(tokenTheRequestCarried)`, sets the new
   bearer on the clone and returns `fetch(clone)`. Whatever that second response is, it goes
   up unchanged: a second 401 is final. A call sent without a token is neither cloned nor
   retried: there is no session to renew. Cost: every request with a token is cloned, an upload included
   (the body is held twice until the response arrives). Accepted at these sizes; the
   alternative is a retry in every query function.
6. **A failed refresh ends the session in one place.** `refreshSession` sets the status to
   `anonymous`. `main.tsx` subscribes once and clears the query cache on that transition, so
   the session module does not know TanStack Query, and the guard's redirect follows from
   the status. The original 401 goes up to the caller.
7. **The middleware is registered explicitly.** `session.ts` imports `api` to call refresh,
   and the middleware imports the session, so `client.ts` cannot import the middleware
   without a cycle. `main.tsx` and the test setup call `api.use(authMiddleware)`. Cost: it
   can be forgotten. It is not silent though: without it every protected call answers 401.
8. **The user is a query, not session state.** `["me"]` through `useMe()`, enabled while the
   status is `authenticated`. The session knows only whether there is a token.
9. **Routes in declarative mode.** `/login` and `/register` are public and redirect to `/`
   when a session exists. Everything else sits under `RequireSession`, which renders the
   outlet, nothing (loading), the "cannot reach the server" page with a retry (unreachable)
    or a redirect to `/login` that remembers where the user wanted
   to go. No loaders: TanStack Query owns the data. `/` shows a home page ("Signed in as",
   logout); the ping card and its query are removed with it, their job is done.
10. **Forms: React Hook Form with zod through the resolver.** The zod schema mirrors the
    server's rules for fast feedback; the server stays the authority (ADR-008: zod for form
    input only). Server answers: `validation-failed` maps `errors[]` onto the fields with
    `setError`; every other problem shows its `detail` in one alert above the form; a
    problem without a `type` (the generic 500) also shows the `requestId`, small. Clients
    switch on `type`, never on `detail`.
11. **Register, then log in.** The API returns no token on registration, so the page calls
    login with the same credentials right after the 201. One extra call, and the user does
    not type the password twice.
12. **Logout always ends the local session.** `POST /api/auth/logout`, then (also when the
    call fails) clear the token, which clears the query cache through fork 6. The access
    token already issued stays valid for its remaining minutes; that is ADR-007's accepted
    gap, not something the frontend can close.
13. **Tests on MSW from here on.** `setupServer` in the test setup, unhandled requests are
    an error, handlers are set per test. The mock cannot model an `HttpOnly` cookie in
    jsdom, so a handler decides from its own state whether a refresh succeeds, and counts
    the calls. That count is the assertion for single flight. The two tests that stub
    `fetch` by hand disappear with the ping card.
14. **shadcn components arrive with their first use:** `input`, `label` and `card` with the
    forms. Whether the CLI's form or field wrapper is worth taking is decided with its
    source on screen.

## Build order

### Session and middleware
1. Dependency: `msw@2` (dev). Test setup: server lifecycle, session reset.
   `src/auth/session.ts`: state, `subscribe`, `getSession`, `signIn(token)`, `signOut()`,
   `refreshSession(failedToken?)`, a reset for tests.
   Tests: a successful refresh stores the token and ends `authenticated`; a 401 ends
   `anonymous`; two calls at once cause one request; a call with a failed token that is
   already replaced causes none.
2. `src/auth/authMiddleware.ts`, registered in `main.tsx` and in the test setup.
   Tests: a protected call carries the bearer and an auth call does not; a 401 refreshes
   and the retry succeeds; a `POST` with a body arrives intact on the retry; two concurrent
   401s cause one refresh; a failed refresh ends `anonymous` and the caller gets the 401; a
   second 401 after the retry is not refreshed again; a 401 from login is not refreshed.
   Commit: `feat: session module with silent refresh, bearer and 401 retry in the client`.

### Routes
3. Dependency: `react-router`. `useSession()`, `RequireSession`, the route table, `useMe()`,
   the home page with logout, placeholder pages for login and register. `main.tsx`:
   `bootSession()`, the cache subscription, the router. Ping card, its query and its tests
   removed. The test helper gains a router (memory history) and becomes `renderApp`.
   Tests: a reload with a working refresh lands on the home page and shows the user; a
   failed refresh lands on the login page; a backend that does not answer on load shows the
   retry page, and the retry finds the session; the login page is not shown while the status is
   `loading`; a visit to `/login` with a session redirects home; logout calls the API, ends
   on the login page and the next visit to `/` stays there.
   Commit: `feat: routes with a session guard, boot refresh and logout`.

### Forms
4. Dependencies: `react-hook-form`, `zod`, `@hookform/resolvers`. shadcn `input`, `label`,
   `card`. `src/features/auth/`: `schemas.ts`, `mutations.ts` (`useLogin`, `useRegister`),
   `LoginPage.tsx`, `RegisterPage.tsx`, one component that renders a problem.
   Tests: login with valid input lands on the page the user came for; a wrong password
   shows the 401 `detail`; an empty form shows the schema's messages and sends nothing;
   register maps a `validation-failed` answer onto its fields; a taken email shows the 409
   `detail`; a successful registration ends logged in.
   Commit: `feat: login and register forms with schema validation and Problem Details`.

### Docs
5. README: the auth flow in the frontend section. Ladder: the row for the token split names
   the frontend half as built, the two-tab gap stays open until Session 6. `PROGRESS.md`,
   this file's status and as-built notes.
   Commit: `docs: SPA auth in the README and on the ladder, plan status`.

## Checks by hand, once the forms exist

- Start the backend with `TEAMVAULT_JWT_TTL=30s` for this run. The decoder uses Spring's
  default validators, which allow 60 s of clock skew, so the 401 arrives after about 90 s.
  Log in, wait, click: the network tab shows 401, refresh, the retried call.
- Reload: one refresh call, not two, with StrictMode on.
- While logged in, open the app through a link on another site: the expectation is the home
  page, because the refresh is a same-site fetch from our own page. Record the result here.
- Application tab: `refresh_token` is `HttpOnly`, `SameSite=Strict`, path `/api/auth`.

## Done when

- `npm run check` is green in `frontend/`.
- Register, login, reload, logout work on `:5173` against the backend.
- The four checks by hand are done and their results stand in the status paragraph.
