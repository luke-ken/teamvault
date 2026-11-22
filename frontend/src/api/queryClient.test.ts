import { expect, test } from "vitest"
import { ApiError } from "@/api/errors.ts"
import { shouldRetry } from "@/api/queryClient.ts"

test("does not retry a client error", () => {
    const forbidden = new ApiError({ title: "Forbidden", status: 403 })

    expect(shouldRetry(0, forbidden)).toBe(false)
})

test("retries a server error twice, then gives up", () => {
    const unavailable = new ApiError({
        title: "Service Unavailable",
        status: 503,
    })

    expect(shouldRetry(0, unavailable)).toBe(true)
    expect(shouldRetry(1, unavailable)).toBe(true)
    expect(shouldRetry(2, unavailable)).toBe(false)
})

test("retries when no response arrived at all", () => {
    expect(shouldRetry(0, new TypeError("Failed to fetch"))).toBe(true)
})
