import { Link } from "react-router"

export const RegisterPage = () => {
    return (
        <main className="flex min-h-svh items-center justify-center p-4">
            <section className="w-full max-w-sm rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
                <h1 className="text-2xl font-semibold">Register</h1>
                <p className="mt-1 text-sm text-muted-foreground">
                    Already registered?{" "}
                    <Link className="underline" to="/login">
                        Log in
                    </Link>
                </p>
            </section>
        </main>
    )
}
