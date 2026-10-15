import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

/** Único `.env` del proyecto, en la raíz del repo (lo comparte con el backend). */
const ENV_DIR = path.resolve(import.meta.dirname, '..')

/**
 * Un build no puede salir con datos simulados ni con una API pública sin HTTPS
 * (localhost se permite para probar el build en la propia máquina).
 */
function assertBuildEnv(env: Record<string, string>) {
  const problems: string[] = []
  const apiUrl = env.VITE_API_BASE_URL ?? ''
  if (env.VITE_MARKETPLACE_DATA_SOURCE !== 'http') problems.push('VITE_MARKETPLACE_DATA_SOURCE debe ser "http" (no se publican datos simulados).')
  if (!/^https:\/\//.test(apiUrl) && !/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(apiUrl)) {
    problems.push('VITE_API_BASE_URL debe ser una URL https:// absoluta (o http://localhost).')
  }
  if (problems.length > 0) {
    throw new Error(`Configuración inválida en .env:\n - ${problems.join('\n - ')}`)
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode, command }) => {
  if (command === 'build') {
    assertBuildEnv(loadEnv(mode, ENV_DIR, 'VITE_'))
  }
  return {
    envDir: ENV_DIR,
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, './src'),
      },
    },
  }
})
