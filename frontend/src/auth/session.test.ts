import { http, HttpResponse } from "msw"
import { expect, test, vi } from "vitest"
import {
    getSession,
    refreshSession,
    signIn,
    subscribe,
} from "@/auth/session.ts"
import { server } from "@/test/server.ts"

const tokenResponse = (token: string) =>
    HttpResponse.json({
        token,
        expiresAt: "2026-11-22T12:15:00Z",
    })

const invalidRefreshToken = () =>
    HttpResponse.json(
        { title: "Unauthorized", status: 401, detail: "Invalid refresh token" },
        {
            status: 401,
            headers: { "Content-Type": "application/problem+json" },
        },
    )

test("a session starts as loading, without a token", () => {
    expect(getSession()).toEqual({ status: "loading", token: null })
})

test("a successful refresh stores the token and ends authenticated", async () => {
    server.use(http.post("*/api/auth/refresh", () => tokenResponse("A2")))

    const token = await refreshSession()

    expect(token).toBe("A2")
    expect(getSession()).toEqual({ status: "authenticated", token: "A2" })
})

test("a rejected refresh ends anonymous", async () => {
    server.use(http.post("*/api/auth/refresh", invalidRefreshToken))

    const token = await refreshSession()

    expect(token).toBeNull()
    expect(getSession()).toEqual({ status: "anonymous", token: null })
})

test("on load, a refresh without any answer ends unreachable", async () => {
    server.use(http.post("*/api/auth/refresh", () => HttpResponse.error()))

    await refreshSession()

    expect(getSession()).toEqual({ status: "unreachable", token: null })
})

test("a retry from unreachable can still find the session", async () => {
    server.use(http.post("*/api/auth/refresh", () => HttpResponse.error()))
    await refreshSession()
    server.use(http.post("*/api/auth/refresh", () => tokenResponse("A2")))

    await refreshSession()

    expect(getSession()).toEqual({ status: "authenticated", token: "A2" })
})

test("a running session survives a refresh without any answer", async () => {
    signIn("A1")
    server.use(http.post("*/api/auth/refresh", () => HttpResponse.error()))

    const token = await refreshSession()

    expect(token).toBeNull()
    expect(getSession()).toEqual({ status: "authenticated", token: "A1" })
})

test("a running session survives a server error on refresh", async () => {
    signIn("A1")
    server.use(
        http.post("*/api/auth/refresh", () =>
            HttpResponse.json(
                { title: "Internal Server Error", status: 500 },
                { status: 500 },
            ),
        ),
    )

    await refreshSession()

    expect(getSession()).toEqual({ status: "authenticated", token: "A1" })
})

test("a running session ends when the server rejects the refresh", async () => {
    signIn("A1")
    server.use(http.post("*/api/auth/refresh", invalidRefreshToken))

    await refreshSession()

    expect(getSession()).toEqual({ status: "anonymous", token: null })
})

test("two refreshes at once cause one request", async () => {
    let calls = 0
    server.use(
        http.post("*/api/auth/refresh", () => {
            calls += 1

            return tokenResponse("A2")
        }),
    )

    const tokens = await Promise.all([refreshSession(), refreshSession()])

    expect(tokens).toEqual(["A2", "A2"])
    expect(calls).toBe(1)
})

test("a refresh after the first one finished is a new request", async () => {
    let calls = 0
    server.use(
        http.post("*/api/auth/refresh", () => {
            calls += 1

            return tokenResponse(`A${calls + 1}`)
        }),
    )

    await refreshSession()
    const token = await refreshSession()

    expect(token).toBe("A3")
    expect(calls).toBe(2)
})

test("a failed token that is already replaced causes no request", async () => {
    signIn("A2")

    // No handler: a request would fail the test as unhandled.
    const token = await refreshSession("A1")

    expect(token).toBe("A2")
})

test("subscribers hear every change until they unsubscribe", () => {
    const listener = vi.fn()
    const unsubscribe = subscribe(listener)

    signIn("A1")
    unsubscribe()
    signIn("A2")

    expect(listener).toHaveBeenCalledTimes(1)
})
