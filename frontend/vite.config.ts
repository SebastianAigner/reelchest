import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'
export default defineConfig({
  plugins: [
    react(),
    {
      name: "strip-use-client-directive",
      enforce: "pre",
      transform(code, id) {
        if (!id.includes("/node_modules/")) return null
        if (!code.startsWith("\"use client\"") && !code.startsWith("'use client'")) return null
        return {
          code: code.replace(/^(['"]use client['"];?\s*)/, ""),
          map: null
        }
      }
    }
  ],
  server: {
    host: '0.0.0.0',
    proxy: {
      "/api": backendUrl,
      "/ul": backendUrl,
      "/decrypt": backendUrl
    }
  }
})
