/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** `mock` (por defecto) o `http`. */
  readonly VITE_MARKETPLACE_DATA_SOURCE?: 'mock' | 'http'
  /** URL base de la API con el prefijo de versión, p. ej. `http://localhost:8080/api/v1`. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
