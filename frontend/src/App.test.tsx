import { screen } from "@testing-library/react"
import { afterEach, expect, test, vi } from "vitest"
import App from "./App.tsx"
import userEvent from "@testing-library/user-event"
import { renderWithQueryClient } from "@/test/render.tsx"

const stubFetch = (status: number, contentType: string, body: unknown) => {
    vi.stubGlobal(
        "fetch",
        vi.fn(async () => {
            return new Response(JSON.stringify(body), {
                status,
                headers: { "Content-Type": contentType },
            })
        }),
    )
}

afterEach(() => {
    vi.unstubAllGlobals()
})

test("shows the application name as the page heading", () => {
    stubFetch(200, "application/json", { message: "pong" })

    renderWithQueryClient(<App />)

    expect(
        screen.getByRole("heading", { level: 1, name: "TeamVault" }),
    ).toBeInTheDocument()
})

test("shows the answer of the backend's ping", async () => {
    stubFetch(200, "application/json", { message: "pong" })

    renderWithQueryClient(<App />)

    expect(screen.getByText("Asking the backend...")).toBeInTheDocument()
    expect(await screen.findByText("Backend says: pong")).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledOnce()
})

test("shows the problem title when the backend answers with an error", async () => {
    stubFetch(500, "application/problem+json", {
        title: "Internal Server Error",
        status: 500,
    })

    renderWithQueryClient(<App />)

    expect(await screen.findByRole("alert")).toHaveTextContent(
        "Backend call failed: Internal Server Error",
    )
})

test("shows a fallback when the backend cannot be reached", async () => {
    vi.stubGlobal(
        "fetch",
        vi.fn(async () => {
            throw new TypeError("Failed to fetch")
        }),
    )

    renderWithQueryClient(<App />)

    expect(await screen.findByRole("alert")).toHaveTextContent(
        "Backend call failed: Backend not reachable",
    )
})

test("asks the backend again when the button is pressed", async () => {
    stubFetch(200, "application/json", { message: "pong" })
    const user = userEvent.setup()

    renderWithQueryClient(<App />)
    await screen.findByText("Backend says: pong")
    await user.click(screen.getByRole("button", { name: "Ping again" }))

    expect(await screen.findByText("Backend says: pong")).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledTimes(2)
})
