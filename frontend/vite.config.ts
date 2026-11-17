import react from "@vitejs/plugin-react"
import { defineConfig } from "vitest/config"

// https://vite.dev/config/
export default defineConfig({
    plugins: [react()],
    server: {
        // One origin in dev: the browser only knows :5173, so no CORS anywhere.
        proxy: {
            "/api": "http://localhost:8080",
        },
    },
    test: {
        environment: "jsdom",
        setupFiles: "./src/setupTests.ts",
    },
})
