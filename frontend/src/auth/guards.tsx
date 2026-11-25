import { Navigate, Outlet, useLocation } from "react-router"
import type { Location } from "react-router"
import { ServerUnreachablePage } from "@/auth/ServerUnreachablePage.tsx"
import { useSession } from "@/auth/useSession.ts"

type LoginRedirectState = { from?: Location } | null

// Around every route that needs a user. The URL never changes while the session is
// loading or the server is silent, so a reload continues where the user was.
export const RequireSession = () => {
    const { status } = useSession()
    const location = useLocation()

    if (status === "loading") return null
    if (status === "unreachable") return <ServerUnreachablePage />
    if (status === "anonymous") {
        return <Navigate to="/login" replace state={{ from: location }} />
    }

    return <Outlet />
}

// Around login and register. With a session there is nothing to do here: back to the
// page the user came for, or home.
export const AnonymousOnly = () => {
    const { status } = useSession()
    const state = useLocation().state as LoginRedirectState

    if (status === "loading") return null
    if (status === "unreachable") return <ServerUnreachablePage />
    if (status === "authenticated") {
        return <Navigate to={state?.from ?? "/"} replace />
    }

    return <Outlet />
}
