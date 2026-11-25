import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render } from "@testing-library/react"
import { MemoryRouter } from "react-router"
import App from "@/App.tsx"
import { clearCacheWithoutSession } from "@/auth/sessionCache.ts"

// The app as main.tsx builds it, at the given URL, without the refresh on load: a test
// decides itself whether it starts with a session, with a refresh or with neither.
// A fresh cache per test, so no test sees another one's data. No retries: a failing
// call should fail the test at once, not after the backoff.
export const renderApp = (url = "/") => {
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false } },
    })
    clearCacheWithoutSession(queryClient)

    return render(
        <QueryClientProvider client={queryClient}>
            <MemoryRouter initialEntries={[url]}>
                <App />
            </MemoryRouter>
        </QueryClientProvider>,
    )
}
