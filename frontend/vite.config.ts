import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    proxy: {
      "/api": backendUrl,
      "/ul": backendUrl
    }
  }
})
