/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** `mock` (por defecto) o `http`. */
  readonly VITE_MARKETPLACE_DATA_SOURCE?: 'mock' | 'http'
  /** URL base de la API con el prefijo de versión, p. ej. `http://localhost:8081/api/v1`. */
  readonly VITE_API_BASE_URL?: string
  /** Correo público de soporte (opcional); sin él no se muestra el botón "Contactar soporte". */
  readonly VITE_SUPPORT_EMAIL?: string
  /** ID de cliente OAuth de Google (ingreso social). Sin él no se muestra el botón de Google. */
  readonly VITE_GOOGLE_CLIENT_ID?: string
  /** ID de la app de Meta (ingreso con Facebook). Sin él no se muestra el botón de Facebook. */
  readonly VITE_FACEBOOK_APP_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
