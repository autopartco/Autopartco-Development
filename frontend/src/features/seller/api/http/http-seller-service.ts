import { apiRequest } from '@/lib/api'
import type { SellerApplicationService, StoreApplicationStatus } from '../types'

/** `SellerSignupResponse` (storesuite/onboarding). Solo los campos que usa la UI. */
interface SellerSignupResponseDto {
  account: { email: string; verificationEmailSent: boolean }
  store: { businessName: string }
  applicationId: string
  applicationStatus: StoreApplicationStatus
  planCode: string | null
}

/**
 * `POST /store-applications` (público en `WebSecurityConfig.PUBLIC_ROUTES`), multipart.
 * La parte `request` va como JSON con su propio Content-Type para que Spring la lea
 * con `@RequestPart`. Los errores de validación llegan con claves como `account.email`
 * o `store.nit`.
 */
export function createHttpSellerApplicationService(): SellerApplicationService {
  return {
    simulated: false,

    async submit(input) {
      const form = new FormData()
      const request = {
        account: { ...input.account, email: input.account.email.trim() },
        store: { ...input.store, businessEmail: input.store.businessEmail.trim() },
        legalType: input.legalType,
      }
      form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
      for (const [key, file] of Object.entries(input.documents)) {
        if (file) form.append(key, file, file.name)
      }
      const dto = await apiRequest<SellerSignupResponseDto>('/store-applications', { method: 'POST', body: form })
      return {
        email: dto.account.email,
        businessName: dto.store.businessName,
        verificationEmailSent: dto.account.verificationEmailSent,
        status: dto.applicationStatus,
        planCode: dto.planCode ?? undefined,
      }
    },
  }
}
