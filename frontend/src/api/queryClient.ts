import { QueryClient } from "@tanstack/react-query"
import { ApiError } from "@/api/errors.ts"

// A 4xx will not get better by asking again: 403 stays 403. Everything else
// (5xx, no connection) gets two more attempts.
export const shouldRetry = (failureCount: number, error: Error) => {
    const isClientError =
        error instanceof ApiError &&
        error.problem.status >= 400 &&
        error.problem.status < 500

    return !isClientError && failureCount < 2
}

export const createQueryClient = () =>
    new QueryClient({
        defaultOptions: {
            queries: {
                // Own writes are covered by invalidation. Thirty seconds collapses a
                // burst of focus events into one refetch for a colleague's changes.
                staleTime: 30_000,
                retry: shouldRetry,
            },
        },
    })
