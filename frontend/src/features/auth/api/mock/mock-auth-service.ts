import { ApiError, simulateLatency } from '@/lib/api'
import { getSession } from '../../session-store'
import type { AuthService, Session } from '../types'

/**
 * Cuentas simuladas para desarrollar la UI sin backend. No hay usuarios reales:
 * cualquier correo y contraseña con formato válido "ingresa", y solo se guarda en
 * `sessionStorage` un usuario simulado (nombre y correo, marcado como tal). Nunca
 * se guardan contraseñas ni tokens.
 *
 * Tipo de cuenta simulado (claim `roles` del token real), según cómo empiece el correo:
 * - `cliente...` -> `["ROLE_CUSTOMER"]` (cliente del marketplace).
 * - `admin...` -> `["ROLE_ADMIN"]` (rol sin menú propio: se degrada a datos y salir).
 * - Cualquier otro -> `["ROLE_CUSTOMER","ROLE_STORE_MANAGER"]` (gerente de almacén; así el portal
 *   simulado sigue funcionando con cualquier correo, como antes).
 *
 * Casos para probar errores:
 * - Ingresar con un correo que empiece por `error` -> credenciales no válidas.
 * - Registrar `existe@autopartco.co` -> el correo ya tiene cuenta.
 * - Verificar o restablecer con el token `vencido` -> token expirado.
 */
const STORAGE_KEY = 'autopartco:sesion-simulada'

function nameFromEmail(email: string): string {
  const local = email.split('@')[0] ?? email
  const words = local.replace(/[._-]+/g, ' ').trim()
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : 'Cliente'
}

/** Roles simulados a partir del correo (ver la descripción de este archivo). */
export function mockRolesFor(email: string): string[] {
  const clean = email.trim().toLowerCase()
  if (clean.startsWith('cliente')) return ['ROLE_CUSTOMER']
  if (clean.startsWith('admin')) return ['ROLE_ADMIN']
  return ['ROLE_CUSTOMER', 'ROLE_STORE_MANAGER']
}

function readStored(): Session | undefined {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return undefined
    const parsed = JSON.parse(raw) as { name?: unknown; email?: unknown }
    if (typeof parsed.name !== 'string' || typeof parsed.email !== 'string') return undefined
    return { user: { name: parsed.name, email: parsed.email }, roles: mockRolesFor(parsed.email), simulated: true }
  } catch {
    return undefined
  }
}

function expired(): never {
  throw new ApiError('El enlace o código venció. Pide uno nuevo.', { status: 400, code: 'TOKEN_EXPIRED' })
}

export function createMockAuthService(): AuthService {
  return {
    simulated: true,

    restore: readStored,

    async getAccessToken() {
      // Token de relleno: los servicios mock no lo validan; solo permite simular el 401 sin sesión.
      return getSession() ? 'mock-access-token' : undefined
    },

    async login({ email }) {
      await simulateLatency()
      const clean = email.trim().toLowerCase()
      if (clean.startsWith('error')) {
        throw new ApiError('Las credenciales no son válidas.', { status: 401, code: 'UNAUTHORIZED' })
      }
      const session: Session = { user: { name: nameFromEmail(clean), email: clean }, roles: mockRolesFor(clean), simulated: true }
      try {
        window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session.user))
      } catch {
        // Sin almacenamiento disponible la sesión simulada dura hasta recargar.
      }
      return session
    },

    async logout() {
      await simulateLatency(250)
      try {
        window.sessionStorage.removeItem(STORAGE_KEY)
      } catch {
        // Nada que limpiar.
      }
    },

    async register(input) {
      await simulateLatency()
      if (input.email.trim().toLowerCase() === 'existe@autopartco.co') {
        throw new ApiError('Ya existe una cuenta con este correo.', { status: 409, code: 'EMAIL_ALREADY_EXISTS' })
      }
      const expiresAt = new Date(Date.now() + 30 * 60 * 1000).toISOString().slice(0, 19)
      return { email: input.email.trim(), verificationEmailSent: true, verificationExpiresAt: expiresAt }
    },

    async verifyEmail(token) {
      await simulateLatency()
      if (token === 'vencido') expired()
      return { email: 'cliente@ejemplo.co', message: 'Correo verificado correctamente.' }
    },

    async requestPasswordRecovery() {
      await simulateLatency()
      return 'Si el correo está registrado, recibirás instrucciones para recuperar tu contraseña.'
    },

    async resetPassword(token) {
      await simulateLatency()
      if (token.trim() === 'vencido') expired()
      return 'Tu contraseña fue actualizada.'
    },
  }
}
