import { useEffect, useState } from "react"
import { api } from "./api/client.ts"
import { Button } from "@/components/ui/button.tsx"

type Ping =
    | { state: "waiting" }
    | { state: "answer"; message: string }
    | { state: "failure"; reason: string }

function App() {
    const [ping, setPing] = useState<Ping>({ state: "waiting" })
    const [attempt, setAttempt] = useState(0)

    // A plain effect on purpose, for one commit: TanStack Query replaces it next.
    useEffect(() => {
        let ignore = false
        api.GET("/api/ping")
            .then(({ data, error }) => {
                if (ignore) return
                setPing(
                    data
                        ? { state: "answer", message: data.message }
                        : { state: "failure", reason: error.title },
                )
            })
            .catch(() => {
                if (ignore) return
                setPing({ state: "failure", reason: "Backend not reachable" })
            })

        return () => {
            ignore = true
        }
    }, [attempt])

    const pingAgain = () => {
        setPing({ state: "waiting" })
        setAttempt((current) => current + 1)
    }

    return (
        <main className="flex min-h-svh items-center justify-center p-4">
            <section className="w-full max-w-sm rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
                <h1 className="text-2xl font-semibold">TeamVault</h1>
                <p className="mt-1 text-sm text-muted-foreground">
                    File sharing for teams. The frontend starts here.
                </p>
                <div className="mt-6 min-h-6 text-sm">
                    {ping.state === "waiting" && <p>Asking the backend...</p>}
                    {ping.state === "answer" && (
                        <p>Backend says: {ping.message}</p>
                    )}
                    {ping.state === "failure" && (
                        <p role="alert" className="text-destructive">
                            Backend call failed: {ping.reason}
                        </p>
                    )}
                </div>
                <Button
                    className="mt-4"
                    onClick={pingAgain}
                    disabled={ping.state === "waiting"}
                >
                    Ping again
                </Button>
            </section>
        </main>
    )
}

export default App
