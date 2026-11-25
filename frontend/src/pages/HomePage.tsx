import { useMe } from "@/features/me/queries.ts"
import { useLogout } from "@/features/auth/mutations.ts"
import { ApiError } from "@/api/errors.ts"
import { Button } from "@/components/ui/button.tsx"

export const HomePage = () => {
    const me = useMe()
    const logout = useLogout()

    return (
        <main className="flex min-h-svh items-center justify-center p-4">
            <section className="w-full max-w-sm rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
                <h1 className="text-2xl font-semibold">TeamVault</h1>
                <div className="mt-1 min-h-5 text-sm text-muted-foreground">
                    {me.isPending && <p>Loading your account...</p>}
                    {me.isSuccess && <p>Signed in as {me.data.displayName}</p>}
                    {me.isError && (
                        <p role="alert" className="text-destructive">
                            {me.error instanceof ApiError
                                ? me.error.problem.detail
                                : "Backend not reachable"}
                        </p>
                    )}
                </div>
                <Button
                    className="mt-6"
                    variant="outline"
                    onClick={() => logout.mutate()}
                    disabled={logout.isPending}
                >
                    Log out
                </Button>
            </section>
        </main>
    )
}
