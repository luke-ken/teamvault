import { http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import { api } from "@/api/client.ts"
import { getSession, signIn } from "@/auth/session.ts"
import { server } from "@/test/server.ts"

const me = { id: "u1", email: "ada@example.com", displayName: "Ada" }

const unauthorized = () =>
    HttpResponse.json(
        { title: "Unauthorized", status: 401, detail: "Invalid token" },
        {
            status: 401,
            headers: { "Content-Type": "application/problem+json" },
        },
    )

const tokenResponse = (token: string) =>
    HttpResponse.json({ token, expiresAt: "2026-11-22T12:15:00Z" })

const meAcceptingOnly = (token: string, seen: string[] = []) =>
    http.get("*/api/me", ({ request }) => {
        const header = request.headers.get("Authorization")
        seen.push(header ?? "none")

        return header === `Bearer ${token}`
            ? HttpResponse.json(me)
            : unauthorized()
    })

const refreshAnswering = (answer: () => Response, calls: { count: number }) =>
    http.post("*/api/auth/refresh", () => {
        calls.count += 1

        return answer()
    })

test("a protected call carries the bearer", async () => {
    signIn("A1")
    const seen: string[] = []
    server.use(meAcceptingOnly("A1", seen))

    const { data } = await api.GET("/api/me")

    expect(data).toEqual(me)
    expect(seen).toEqual(["Bearer A1"])
})

test("an auth call carries no bearer, even with a session", async () => {
    signIn("A1")
    let header: string | null = "not called"
    server.use(
        http.post("*/api/auth/login", ({ request }) => {
            header = request.headers.get("Authorization")
            return tokenResponse("A2")
        }),
    )

    await api.POST("/api/auth/login", {
        body: { email: "ada@example.com", password: "secret" },
    })

    expect(header).toBeNull()
})

test("a 401 refreshes once and the retry carries the new token", async () => {
    signIn("A1")
    const seen: string[] = []
    const refreshes = { count: 0 }
    server.use(
        meAcceptingOnly("A2", seen),
        refreshAnswering(() => tokenResponse("A2"), refreshes),
    )

    const { data, error } = await api.GET("/api/me")

    expect(error).toBeUndefined()
    expect(data).toEqual(me)
    expect(seen).toEqual(["Bearer A1", "Bearer A2"])
    expect(refreshes.count).toBe(1)
    expect(getSession().token).toBe("A2")
})

test("a request body arrives intact on the retry", async () => {
    signIn("A1")
    const bodies: unknown[] = []
    server.use(
        http.post("*/api/companies", async ({ request }) => {
            bodies.push(await request.json())

            return request.headers.get("Authorization") === "Bearer A2"
                ? HttpResponse.json({ id: "c1" }, { status: 201 })
                : unauthorized()
        }),
        refreshAnswering(() => tokenResponse("A2"), { count: 0 }),
    )

    const { response } = await api.POST("/api/companies", {
        body: { name: "Acme", tier: "SMALL" },
    })

    expect(response.status).toBe(201)
    expect(bodies).toEqual([
        { name: "Acme", tier: "SMALL" },
        { name: "Acme", tier: "SMALL" },
    ])
})

test("two calls that fail at once share one refresh", async () => {
    signIn("A1")
    const refreshes = { count: 0 }
    server.use(
        meAcceptingOnly("A2"),
        refreshAnswering(() => tokenResponse("A2"), refreshes),
    )

    const results = await Promise.all([api.GET("/api/me"), api.GET("/api/me")])

    expect(results.map((result) => result.data)).toEqual([me, me])
    expect(refreshes.count).toBe(1)
})

test("a rejected refresh ends the session and the caller gets the 401", async () => {
    signIn("A1")
    server.use(
        meAcceptingOnly("A2"),
        refreshAnswering(unauthorized, { count: 0 }),
    )

    const { error, response } = await api.GET("/api/me")

    expect(response.status).toBe(401)
    expect(error?.detail).toBe("Invalid token")
    expect(getSession().status).toBe("anonymous")
})

test("a second 401 after the retry is final", async () => {
    signIn("A1")
    const seen: string[] = []
    const refreshes = { count: 0 }
    server.use(
        meAcceptingOnly("never valid", seen),
        refreshAnswering(() => tokenResponse("A2"), refreshes),
    )

    const { response } = await api.GET("/api/me")

    expect(response.status).toBe(401)
    expect(seen).toEqual(["Bearer A1", "Bearer A2"])
    expect(refreshes.count).toBe(1)
})

test("a 401 from login does not refresh", async () => {
    signIn("A1")
    // No refresh handler: a refresh call would fail the test as unhandled.
    server.use(http.post("*/api/auth/login", unauthorized))

    const { response } = await api.POST("/api/auth/login", {
        body: { email: "ada@example.com", password: "wrong" },
    })

    expect(response.status).toBe(401)
    expect(getSession().token).toBe("A1")
})

test("a call without a session gets its 401 without a refresh", async () => {
    const seen: string[] = []
    // No refresh handler here either.
    server.use(meAcceptingOnly("A1", seen))

    const { response } = await api.GET("/api/me")

    expect(response.status).toBe(401)
    expect(seen).toEqual(["none"])
})
