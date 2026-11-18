import react from "@vitejs/plugin-react"
import path from "node:path"
import { defineConfig } from "vitest/config"
import tailwindcss from "@tailwindcss/vite"

// https://vite.dev/config/
export default defineConfig({
    plugins: [react(), tailwindcss()],
    resolve: {
        alias: {
            "@": path.resolve(import.meta.dirname, "./src"),
        },
    },
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
