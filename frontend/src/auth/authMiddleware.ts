import type { Middleware } from "openapi-fetch"
import { getSession, refreshSession } from "@/auth/session.ts"

type SentRequest = {
    // A body can be read once, so the retry needs a copy made before the first send.
    copy: Request
    token: string
}

// Calls that carried a token and wait for their response, by openapi-fetch's call id.
const sentRequests = new Map<string, SentRequest>()

// Login, register, refresh and logout: no bearer, and their 401 is an answer, not a
// reason to refresh.
const isAuthPath = (schemaPath: string) => schemaPath.startsWith("/api/auth/")

export const authMiddleware: Middleware = {
    onRequest: ({ request, schemaPath, id }) => {
        const { token } = getSession()
        if (isAuthPath(schemaPath) || !token) {
            return
        }
        request.headers.set("Authorization", `Bearer ${token}`)
        sentRequests.set(id, { copy: request.clone(), token })
    },

    onResponse: async ({ response, id }) => {
        const sent = sentRequests.get(id)
        sentRequests.delete(id)
        if (!sent || response.status !== 401) {
            return
        }
        const token = await refreshSession(sent.token)
        if (!token) {
            // No new token: 401 goes up as it is.
            return
        }
        sent.copy.headers.set("Authorization", `Bearer ${token}`)

        // Once. Whatever this second response is, it goes up unchanged.
        return globalThis.fetch(sent.copy)
    },

    onError: ({ id }) => {
        sentRequests.delete(id)
    },
}
