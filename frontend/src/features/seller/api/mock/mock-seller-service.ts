import { ApiError, simulateLatency } from '@/lib/api'
import { promoteMockSession } from '@/features/auth/api/mock/mock-auth-service'
import { getSession } from '@/features/auth/session-store'
import type { MyStoreApplication, SellerApplicationService } from '../types'

const COOLDOWN_MS = 5 * 60 * 1000
const SEEDED_ID = '00000000-0000-4000-8000-000000000001'
const SEEDED_STORE_ID = '00000000-0000-4000-8000-000000000002'

/**
 * Solicitud de almacén simulada: valida y responde como el backend, pero no envía
 * ni guarda nada (los archivos elegidos no salen del navegador).
 * Para probar el error de correo repetido, usa `existe@autopartco.co`. La solicitud con
 * sesión se recuerda solo en memoria, para ver el estado "en revisión" al volver.
 *
 * Cómo probar la configuración del almacén (`/seller/setup`):
 * - Con token: `/seller/setup?token=cualquiera` configura; `?token=vencido` muestra la pantalla de
 *   enlace vencido. «Enviarme un nuevo enlace» responde siempre igual; repetirlo antes de 5 minutos
 *   simula el 429 `SELLER_SETUP_LINK_COOLDOWN`.
 * - Con sesión: ingresa con un correo que empiece por `pendiente` (p. ej. `pendiente@autopartco.co`).
 *   Esa cuenta es cliente y tiene una solicitud `CONFIGURATION_PENDING`, así que el ingreso lleva a
 *   `/seller/setup`. Al terminar pasa a gerente (cierra sesión para volver a empezar).
 */
export function createMockSellerApplicationService(): SellerApplicationService {
  const mine: MyStoreApplication[] = []
  const lastLinkAt = new Map<string, number>()

  /** Solicitud simulada de las cuentas `pendiente...`: pendiente de configurar hasta que la cuenta pasa a gerente. */
  function seeded(): MyStoreApplication[] {
    const session = getSession()
    if (!session?.user.email.startsWith('pendiente')) return []
    const done = session.roles.includes('ROLE_STORE_MANAGER')
    return [
      {
        applicationId: SEEDED_ID,
        storeId: SEEDED_STORE_ID,
        businessName: 'Repuestos El Paisa',
        status: done ? 'ACTIVE' : 'CONFIGURATION_PENDING',
        createdAt: '2026-10-10T09:00:00',
      },
    ]
  }

  /** Enfriamiento de 5 min por solicitud o token, como el backend. */
  function throttle(key: string): void {
    const last = lastLinkAt.get(key)
    if (last && Date.now() - last < COOLDOWN_MS) {
      throw new ApiError('Ya te enviamos un enlace hace poco. Espera 5 minutos para pedir otro.', {
        status: 429,
        code: 'SELLER_SETUP_LINK_COOLDOWN',
      })
    }
    lastLinkAt.set(key, Date.now())
  }

  function requireOwnPending(applicationId: string): void {
    if (!getSession()) throw new ApiError('Inicia sesión para continuar.', { status: 401, code: 'UNAUTHORIZED' })
    const application = [...seeded(), ...mine].find((item) => item.applicationId === applicationId)
    if (!application) {
      throw new ApiError('No encontramos esa solicitud.', { status: 404, code: 'SELLER_APPLICATION_NOT_FOUND' })
    }
    if (application.status !== 'CONFIGURATION_PENDING') {
      throw new ApiError('Esta solicitud ya no está pendiente de configuración.', { status: 409, code: 'SELLER_SETUP_NOT_PENDING' })
    }
  }

  return {
    simulated: true,

    async submit(input) {
      await simulateLatency(900)
      if (input.account.email.trim().toLowerCase() === 'existe@autopartco.co') {
        throw new ApiError('Ya existe una cuenta con este correo.', { status: 409, code: 'EMAIL_ALREADY_EXISTS' })
      }
      return {
        email: input.account.email.trim(),
        businessName: input.store.businessName.trim(),
        verificationEmailSent: true,
        status: 'PENDING_REVIEW',
        planCode: 'TRY',
      }
    },

    async applyAsCurrentUser(input) {
      await simulateLatency(900)
      if (mine.some((application) => application.status !== 'REJECTED')) {
        throw new ApiError('Ya tienes una solicitud de vendedor en curso.', {
          status: 409,
          code: 'SELLER_APPLICATION_ALREADY_EXISTS',
        })
      }
      const application: MyStoreApplication = {
        applicationId: crypto.randomUUID(),
        storeId: crypto.randomUUID(),
        businessName: input.store.businessName.trim(),
        status: 'PENDING_REVIEW',
        createdAt: new Date().toISOString().slice(0, 19),
      }
      mine.unshift(application)
      return { applicationId: application.applicationId, storeId: application.storeId, status: application.status, planCode: 'TRY' }
    },

    async myApplications() {
      await simulateLatency(300)
      return [...seeded(), ...mine].map((application) => ({ ...application }))
    },

    async completeSetup(input) {
      await simulateLatency(900)
      if (!input.token.trim() || input.token.trim() === 'vencido') {
        throw new ApiError('El enlace de configuración venció o ya fue utilizado.', {
          status: 400,
          code: 'SELLER_SETUP_TOKEN_INVALID',
        })
      }
      return {
        storeId: crypto.randomUUID(),
        status: 'ACTIVE',
        trialEndsAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      }
    },

    async completeSetupAuthenticated(applicationId) {
      await simulateLatency(900)
      requireOwnPending(applicationId)
      promoteMockSession()
      return {
        storeId: SEEDED_STORE_ID,
        status: 'ACTIVE',
        trialEndsAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      }
    },

    async requestSetupLink(applicationId) {
      await simulateLatency(600)
      requireOwnPending(applicationId)
      throttle(applicationId)
    },

    async requestSetupLinkByToken(token) {
      await simulateLatency(600)
      // El endpoint real siempre responde 204; aquí solo se simula el enfriamiento para poder probar el 429.
      if (token.trim()) throttle(`token:${token.trim()}`)
    },
  }
}
