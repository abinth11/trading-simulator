import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/user-api": {
        target: "http://localhost:8081",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/user-api/, "")
      },
      "/order-api": {
        target: "http://localhost:8082",
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path.replace(/^\/order-api/, "")
      },
      "/engine-api": {
        target: "http://localhost:8083",
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path.replace(/^\/engine-api/, "")
      },
      "/portfolio-api": {
        target: "http://localhost:8084",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/portfolio-api/, "")
      }
    }
  }
});
