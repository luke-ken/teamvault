import { Route, Routes } from "react-router"
import { AnonymousOnly, RequireSession } from "@/auth/guards.tsx"
import { LoginPage } from "@/features/auth/LoginPage.tsx"
import { RegisterPage } from "@/features/auth/RegisterPage.tsx"
import { HomePage } from "@/pages/HomePage.tsx"
import { NotFoundPage } from "@/pages/NotFoundPage.tsx"

function App() {
    return (
        <Routes>
            <Route element={<AnonymousOnly />}>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
            </Route>
            <Route element={<RequireSession />}>
                <Route path="/" element={<HomePage />} />
                <Route path="*" element={<NotFoundPage />} />
            </Route>
        </Routes>
    )
}

export default App
