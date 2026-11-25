import { act, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { delay, http, HttpResponse } from "msw"
import { expect, test } from "vitest"
import { refreshSession, signIn, signOut } from "@/auth/session.ts"
import { renderApp } from "@/test/render.tsx"
import { server } from "@/test/server.ts"

const users: Record<string, string> = { A1: "Ada", B1: "Grace" }

const unauthorized = () =>
    HttpResponse.json(
        { title: "Unauthorized", status: 401, detail: "Invalid token" },
        {
            status: 401,
            headers: { "Content-Type": "application/problem+json" },
        },
    )

// Answers like the backend: the user who owns the bearer token, 401 without one.
const meByToken = http.get("*/api/me", ({ request }) => {
    const token = request.headers.get("Authorization")?.replace("Bearer ", "")
    const displayName = token ? users[token] : undefined

    return displayName
        ? HttpResponse.json({
              id: token,
              email: "user@example.com",
              displayName,
          })
        : unauthorized()
})

const refreshGives = (token: string) =>
    http.post("*/api/auth/refresh", () =>
        HttpResponse.json({ token, expiresAt: "2026-11-22T12:15:00Z" }),
    )

const refreshRejected = http.post("*/api/auth/refresh", unauthorized)

const refreshSilent = http.post("*/api/auth/refresh", () =>
    HttpResponse.error(),
)

const heading = (name: string) => screen.findByRole("heading", { name })

test("a load with a valid cookie lands on the start page and shows the user", async () => {
    server.use(refreshGives("A1"), meByToken)

    void refreshSession()
    renderApp("/")

    expect(await screen.findByText("Signed in as Ada")).toBeInTheDocument()
})

test("a load without a session lands on the login page", async () => {
    server.use(refreshRejected)

    void refreshSession()
    renderApp("/")

    expect(await heading("Log in")).toBeInTheDocument()
})

test("nothing is shown while the refresh on load is still out", async () => {
    server.use(
        http.post("*/api/auth/refresh", async () => {
            await delay("infinite")

            return unauthorized()
        }),
    )

    void refreshSession()
    const { container } = renderApp("/")

    expect(container).toBeEmptyDOMElement()
})

test("a silent server on load shows the retry page, and the retry finds the session", async () => {
    server.use(refreshSilent)
    const user = userEvent.setup()

    void refreshSession()
    renderApp("/")
    await heading("Cannot reach the server")
    server.use(refreshGives("A1"), meByToken)
    await user.click(screen.getByRole("button", { name: "Try again" }))

    expect(await screen.findByText("Signed in as Ada")).toBeInTheDocument()
})

test("a retry against a server that stays silent says so", async () => {
    server.use(refreshSilent)
    const user = userEvent.setup()

    void refreshSession()
    renderApp("/")
    await heading("Cannot reach the server")
    await user.click(screen.getByRole("button", { name: "Try again" }))

    expect(await screen.findByRole("alert")).toHaveTextContent(
        "Still no answer.",
    )
})

test("the login page sends a user with a session to the start page", async () => {
    server.use(meByToken)
    signIn("A1")

    renderApp("/login")

    expect(await screen.findByText("Signed in as Ada")).toBeInTheDocument()
})

test("after login the user arrives at the page they asked for", async () => {
    server.use(refreshRejected)

    void refreshSession()
    renderApp("/some/deep/page")
    await heading("Log in")
    // What the login form will do in the next step.
    act(() => signIn("A1"))

    expect(await heading("Page not found")).toBeInTheDocument()
})

test("logout calls the API and ends on the login page", async () => {
    let logoutCalls = 0
    server.use(
        meByToken,
        http.post("*/api/auth/logout", () => {
            logoutCalls += 1

            return new HttpResponse(null, { status: 200 })
        }),
    )
    signIn("A1")
    const user = userEvent.setup()

    renderApp("/")
    await screen.findByText("Signed in as Ada")
    await user.click(screen.getByRole("button", { name: "Log out" }))

    expect(await heading("Log in")).toBeInTheDocument()
    expect(logoutCalls).toBe(1)
})

test("logout ends the local session even when the call fails", async () => {
    server.use(
        meByToken,
        http.post("*/api/auth/logout", () => HttpResponse.error()),
    )
    signIn("A1")
    const user = userEvent.setup()

    renderApp("/")
    await screen.findByText("Signed in as Ada")
    await user.click(screen.getByRole("button", { name: "Log out" }))

    expect(await heading("Log in")).toBeInTheDocument()
})

test("the next user of the same browser does not see the previous user's data", async () => {
    server.use(meByToken)
    signIn("A1")

    renderApp("/")
    await screen.findByText("Signed in as Ada")
    act(() => signOut())
    await heading("Log in")
    act(() => signIn("B1"))

    // Checked before the new answer can arrive: a cached "Ada" would be on screen now.
    expect(screen.queryByText("Signed in as Ada")).not.toBeInTheDocument()
    expect(await screen.findByText("Signed in as Grace")).toBeInTheDocument()
})

test("an unknown address shows the not found page", async () => {
    signIn("A1")

    renderApp("/nowhere")

    expect(await heading("Page not found")).toBeInTheDocument()
})
