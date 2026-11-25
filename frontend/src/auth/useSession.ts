import { useSyncExternalStore } from "react"
import { getSession, subscribe } from "@/auth/session.ts"

// The bridge from the session module into React: the component renders again whenever
// the module tells its subscribers that the session changed.
export const useSession = () => useSyncExternalStore(subscribe, getSession)
