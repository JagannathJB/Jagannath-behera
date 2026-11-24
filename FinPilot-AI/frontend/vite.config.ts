import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// In development the browser talks only to Vite (port 5173); Vite forwards API and
// health calls to Spring Boot, so no CORS configuration is needed on the backend.
const backend = process.env.VITE_BACKEND_URL ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: backend, changeOrigin: true },
      "/actuator": { target: backend, changeOrigin: true },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
  },
});
