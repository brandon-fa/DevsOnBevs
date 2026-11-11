import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // 127.0.0.1 avoids Windows Node dual-stack localhost AggregateError (ECONNREFUSED)
      "/api": "http://127.0.0.1:8080",
    },
  },
});
