import { Button } from "@/components/ui/button.tsx"
import { usePing } from "@/features/ping/queries.ts"
import { ApiError } from "@/api/errors.ts"

function App() {
    const ping = usePing()

    return (
        <main className="flex min-h-svh items-center justify-center p-4">
            <section className="w-full max-w-sm rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
                <h1 className="text-2xl font-semibold">TeamVault</h1>
                <p className="mt-1 text-sm text-muted-foreground">
                    File sharing for teams. The frontend starts here.
                </p>
                <div className="mt-6 min-h-6 text-sm">
                    {ping.isPending && <p>Asking the backend...</p>}
                    {ping.isSuccess && <p>Backend says: {ping.data.message}</p>}
                    {ping.isError && (
                        <p role="alert" className="text-destructive">
                            Backend call failed:{" "}
                            {ping.error instanceof ApiError
                                ? ping.error.problem.title
                                : "Backend not reachable"}
                        </p>
                    )}
                </div>
                <Button
                    className="mt-4"
                    onClick={() => ping.refetch()}
                    disabled={ping.isFetching}
                >
                    Ping again
                </Button>
            </section>
        </main>
    )
}

export default App
