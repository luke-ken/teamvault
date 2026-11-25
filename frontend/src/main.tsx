import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import "./index.css"
import App from "./App.tsx"
import { createQueryClient } from "@/api/queryClient.ts"
import { QueryClientProvider } from "@tanstack/react-query"
import { api } from "@/api/client.ts"
import { authMiddleware } from "@/auth/authMiddleware.ts"
import { clearCacheWithoutSession } from "@/auth/sessionCache.ts"
import { refreshSession } from "@/auth/session.ts"
import { BrowserRouter } from "react-router"

api.use(authMiddleware)

const queryClient = createQueryClient()
clearCacheWithoutSession(queryClient)

// The access token lives in memory, so every load starts by asking for a new one. Here
// and not in an effect: StrictMode would run an effect twice, and a second refresh with
// the same cookie revokes the session (ADR-007).
void refreshSession()

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <QueryClientProvider client={queryClient}>
            <BrowserRouter>
                <App />
            </BrowserRouter>
        </QueryClientProvider>
    </StrictMode>,
)
