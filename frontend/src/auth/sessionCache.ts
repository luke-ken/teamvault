import type { QueryClient } from "@tanstack/react-query"
import { getSession, subscribe } from "@/auth/session.ts"

// Cached answers belong to the user who asked. Without a session they are dropped, so
// the next user of this browser never sees them. Returns the unsubscribe function.
export const clearCacheWithoutSession = (queryClient: QueryClient) =>
    subscribe(() => {
        if (getSession().status !== "authenticated") {
            queryClient.clear()
        }
    })
