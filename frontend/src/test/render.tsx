import type { ReactElement } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render } from "@testing-library/react"

// A fresh cache per test, so no test sees another one's data. No retries: a failing
// call should fail the test at once, not after the backoff.
export const renderWithQueryClient = (ui: ReactElement) => {
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false } },
    })

    return render(
        <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
    )
}
