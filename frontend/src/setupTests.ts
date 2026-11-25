import "@testing-library/jest-dom/vitest"
import { cleanup } from "@testing-library/react"
import { afterAll, afterEach, beforeAll } from "vitest"
import { server } from "@/test/server.ts"
import { resetSession } from "@/auth/session.ts"
import { api } from "@/api/client.ts"
import { authMiddleware } from "@/auth/authMiddleware.ts"

api.use(authMiddleware)

// A request without a handler fails the test instead of leaving the process.
beforeAll(() => {
    server.listen({ onUnhandledRequest: "error" })
})

// Without Vitest globals, Testing Library cannot register its own cleanup.
afterEach(() => {
    cleanup()
    server.resetHandlers()
    // The session is module state: without the reset it would leak into the next test.
    resetSession()
})

afterAll(() => {
    server.close()
})
