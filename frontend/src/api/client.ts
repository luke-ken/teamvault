import createClient from "openapi-fetch"
import type { paths } from "./schema"

// One client for the whole app. It always talks to the page's own origin: the dev
// proxy (later Caddy) forwards /api to the backend. Spelled out instead of an empty
// base URL because Node's Request, which the tests run on, rejects relative URLs.
export const api = createClient<paths>({
    baseUrl: window.location.origin,
    // Looked up on every call: a test can replace the global after this module loaded.
    fetch: (request) => globalThis.fetch(request),
})
