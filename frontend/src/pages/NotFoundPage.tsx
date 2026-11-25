import { Link } from "react-router"

export const NotFoundPage = () => {
    return (
        <main className="flex min-h-svh items-center justify-center p-4">
            <section className="w-full max-w-sm rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
                <h1 className="text-2xl font-semibold">Page not found</h1>
                <p className="mt-1 text-sm text-muted-foreground">
                    <Link className="underline" to="/">
                        Back to the start page
                    </Link>
                </p>
            </section>
        </main>
    )
}
