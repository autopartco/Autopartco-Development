import { ApiError, simulateLatency } from '@/lib/api'
import type { MyStoreApplication, SellerApplicationService } from '../types'

/**
 * Solicitud de almacén simulada: valida y responde como el backend, pero no envía
 * ni guarda nada (los archivos elegidos no salen del navegador).
 * Para probar el error de correo repetido, usa `existe@autopartco.co`. La solicitud con
 * sesión se recuerda solo en memoria, para ver el estado "en revisión" al volver.
 */
export function createMockSellerApplicationService(): SellerApplicationService {
  const mine: MyStoreApplication[] = []

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
      return mine.map((application) => ({ ...application }))
    },

    async completeSetup(input) {
      await simulateLatency(900)
      if (!input.token.trim()) {
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
  }
}
