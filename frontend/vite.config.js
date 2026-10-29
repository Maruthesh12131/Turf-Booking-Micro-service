/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server runs on 5173, one of the origins the gateway allows (CORS_ALLOWED_ORIGINS).
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  test: { environment: 'jsdom', globals: true, setupFiles: './src/test/setup.js' },
})
