import { ApiError, simulateLatency } from '@/lib/api'
import type { SellerApplicationService } from '../types'

/**
 * Solicitud de almacén simulada: valida y responde como el backend, pero no envía
 * ni guarda nada (los archivos elegidos no salen del navegador).
 * Para probar el error de correo repetido, usa `existe@autopartco.co`.
 */
export function createMockSellerApplicationService(): SellerApplicationService {
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
  }
}
