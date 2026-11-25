import { useState } from "react"
import { refreshSession } from "@/auth/session.ts"
import { Button } from "@/components/ui/button.tsx"

export const ServerUnreachablePage = () => {
    const [attempt, setAttempt] = useState<"none" | "running" | "failed">(
        "none",
    )

    const tryAgain = async () => {
        setAttempt("running")
        await refreshSession()
        // Only visible when the server stayed silent: any answer changes the session
        // status, and the guard replaces this page.
        setAttempt("failed")
    }

    return (
        <main className="flex min-h-svh items-center justify-center p-4">
            <section className="w-full max-w-sm rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
                <h1 className="text-2xl font-semibold">
                    Cannot reach the server
                </h1>
                <p className="mt-1 text-sm text-muted-foreground">
                    TeamVault did not answer. Your place in the app is kept.
                </p>
                <div className="mt-6 min-h-6 text-sm">
                    {attempt === "failed" && (
                        <p role="alert" className="text-destructive">
                            Still no answer.
                        </p>
                    )}
                </div>
                <Button
                    className="mt-4"
                    onClick={tryAgain}
                    disabled={attempt === "running"}
                >
                    Try again
                </Button>
            </section>
        </main>
    )
}
