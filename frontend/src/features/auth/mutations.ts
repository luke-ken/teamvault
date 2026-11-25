import { useMutation } from "@tanstack/react-query"
import { api } from "@/api/client.ts"
import { signOut } from "@/auth/session.ts"

export const useLogout = () =>
    useMutation({
        mutationFn: () => api.POST("/api/auth/logout"),
        // Also when the call failed: the user asked to leave, so this browser forgets
        // the token either way.
        onSettled: signOut,
    })
