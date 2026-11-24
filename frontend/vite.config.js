import react from '@vitejs/plugin-react'
import process from 'node:process'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Encaminha a API (incluindo o stream SSE) para o Gateway Spring Boot.
      // 127.0.0.1 em vez de localhost: no Windows, "localhost" pode resolver para ::1 (IPv6).
      '/api': {
        target: process.env.VITE_PROXY_TARGET ?? 'http://127.0.0.1:8080',
        // Mantém o Host original (localhost:5173), igual ao Origin do navegador: para o
        // Spring a chamada é same-origin e o CORS não entra em jogo.
        changeOrigin: false,
      },
    },
  },
})
