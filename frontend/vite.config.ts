import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

/**
 * Un build de producción no puede salir con datos simulados ni con una API sin HTTPS:
 * es mejor que falle aquí a que llegue así a los usuarios.
 */
function assertProductionEnv(env: Record<string, string>) {
  const problems: string[] = []
  if (env.VITE_MARKETPLACE_DATA_SOURCE !== 'http') problems.push('VITE_MARKETPLACE_DATA_SOURCE debe ser "http" (no se publican datos simulados).')
  if (!/^https:\/\//.test(env.VITE_API_BASE_URL ?? '')) problems.push('VITE_API_BASE_URL debe ser una URL https:// absoluta.')
  if (problems.length > 0) {
    throw new Error(`Configuración de producción inválida (.env.production):\n - ${problems.join('\n - ')}`)
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode, command }) => {
  if (command === 'build' && mode === 'production') {
    assertProductionEnv(loadEnv(mode, import.meta.dirname, 'VITE_'))
  }
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, './src'),
      },
    },
  }
})
