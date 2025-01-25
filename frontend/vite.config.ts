import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    proxy: {
      "/api": "http://localhost:8080",
      "/ul": "http://localhost:8080"
    }
  }
})
