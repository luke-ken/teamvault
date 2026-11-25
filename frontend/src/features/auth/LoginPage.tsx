import { Link } from "react-router"

export const LoginPage = () => {
    return (
        <main className="flex min-h-svh items-center justify-center p-4">
            <section className="w-full max-w-sm rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
                <h1 className="text-2xl font-semibold">Log in</h1>
                <p className="mt-1 text-sm text-muted-foreground">
                    No account yet?{" "}
                    <Link className="underlined" to="/register">
                        Register
                    </Link>
                </p>
            </section>
        </main>
    )
}
