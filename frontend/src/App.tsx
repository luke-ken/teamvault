import { useEffect, useState } from "react"
import { api } from "./api/client.ts"

type Ping =
    | { state: "waiting" }
    | { state: "answer"; message: string }
    | { state: "failure"; reason: string }

function App() {
    const [ping, setPing] = useState<Ping>({ state: "waiting" })

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
    }, [])

    return (
        <main>
            <h1>TeamVault</h1>
            <p>File sharing for teams. The frontend starts here.</p>
            {ping.state === "waiting" && <p>Asking the backend...</p>}
            {ping.state === "answer" && <p>Backend says: {ping.message}</p>}
            {ping.state === "failure" && (
                <p role="alert">Backend call failed: {ping.reason}</p>
            )}
        </main>
    )
}

export default App
