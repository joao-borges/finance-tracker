import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev server runs on :3000 with HMR; /api/* is proxied to the Spring Boot
// backend on :8080. In a packaged build the UI is instead baked into the jar
// and served by Spring Boot directly (see api/pom.xml), so no proxy is needed.
const apiProxy = process.env.VITE_API_PROXY ?? "http://localhost:8080";

export default defineConfig({
    plugins: [react()],
    server: {
        host: true,
        port: 3000,
        proxy: {
            "/api": {
                target: apiProxy,
                changeOrigin: true,
            },
        },
    },
    build: {
        outDir: "dist",
        emptyOutDir: true,
    },
});
