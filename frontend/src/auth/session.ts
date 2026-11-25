import { api } from "@/api/client.ts"

export type Session = {
    status: "loading" | "authenticated" | "anonymous" | "unreachable"
    token: string | null
}

type Listener = () => void

const initialSession: Session = { status: "loading", token: null }

// Module state, not React state: the client middleware needs the token outside any
// component, and the refresh must not depend on how often a component mounts.
let session = initialSession
let refreshInFlight: Promise<string | null> | null = null
const listeners = new Set<Listener>()

const setSession = (next: Session) => {
    session = next
    listeners.forEach((listener) => listener())
}

export const getSession = () => session

export const subscribe = (listener: Listener) => {
    listeners.add(listener)

    return () => {
        listeners.delete(listener)
    }
}

export const signIn = (token: string) => {
    setSession({ status: "authenticated", token })
}

export const signOut = () => {
    setSession({ status: "anonymous", token: null })
}

const requestRefresh = async () => {
    try {
        const { data, response } = await api.POST("/api/auth/refresh")
        if (data) {
            signIn(data.token)
            return data.token
        }
        if (response.status === 401) {
            signOut()
            return null
        }
    } catch {
        // No answer at all: handled below, like a 5xx.
    }
    // No verdict from the server (network, 5xx). A running session is left alone, the
    // next 401 tries again. On load there is no session yet, and nobody knows whether
    // there is one: that is its own state, not "anonymous".
    if (session.status === "loading") {
        setSession({ status: "unreachable", token: null })
    }
    return null
}

// The only caller of the refresh endpoint. A refresh token works once (ADR-007): a second
// call with the same cookie would revoke the whole family, so concurrent callers share
// one request.
export const refreshSession = (failedToken?: string) => {
    // The token that failed is already replaced, nothing to do
    if (failedToken && session.token && session.token !== failedToken) {
        return Promise.resolve<string | null>(session.token)
    }
    refreshInFlight ??= requestRefresh().finally(() => {
        refreshInFlight = null
    })

    return refreshInFlight
}

// Tests only: module state would otherwise survive from one test to the next.
export const resetSession = () => {
    session = initialSession
    refreshInFlight = null
    listeners.clear()
}
