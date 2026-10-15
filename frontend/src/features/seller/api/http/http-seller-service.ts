import { apiRequest } from '@/lib/api'
import { portalRequest } from '@/features/portal/api/http/portal-http'
import type { SellerApplicationService, SellerDocumentKey, StoreApplicationStatus, StoreRegistration } from '../types'

/** `SellerSignupResponse` (storesuite/onboarding). Solo los campos que usa la UI. */
interface SellerSignupResponseDto {
  account: { email: string; verificationEmailSent: boolean }
  store: { businessName: string }
  applicationId: string
  applicationStatus: StoreApplicationStatus
  planCode: string | null
}

/** `StoreApplicationCreatedResponse`. */
interface StoreApplicationCreatedDto {
  applicationId: string
  storeId: string
  status: StoreApplicationStatus
  planCode: string | null
}

/** `storesuite.onboarding.StoreApplicationResponse`. Solo los campos que usa la UI. */
interface StoreApplicationDto {
  applicationId: string
  storeId: string
  businessName: string
  status: StoreApplicationStatus
  reviewNotes: string | null
  createdAt: string
}

/** `StoreSetupResponse` (storesuite/onboarding). Solo los campos que usa la UI. */
interface StoreSetupDto {
  storeId: string
  status: StoreApplicationStatus
  trialEndsAt: string | null
}

/**
 * Cuerpo multipart: la parte `request` va como JSON con su propio Content-Type para que
 * Spring la lea con `@RequestPart`; cada documento, en su parte con nombre.
 */
function multipart(request: unknown, documents: Partial<Record<SellerDocumentKey, File>>): FormData {
  const form = new FormData()
  form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  for (const [key, file] of Object.entries(documents)) {
    if (file) form.append(key, file, file.name)
  }
  return form
}

function setupForm(input: { businessDescription?: string; logo?: File; banner?: File }, token?: string): FormData {
  // El token va como campo de formulario: el backend lo lee con @RequestParam.
  const form = new FormData()
  if (token) form.append('token', token)
  if (input.businessDescription?.trim()) form.append('businessDescription', input.businessDescription.trim())
  if (input.logo) form.append('logo', input.logo, input.logo.name)
  if (input.banner) form.append('banner', input.banner, input.banner.name)
  return form
}

const trimStore = (store: StoreRegistration): StoreRegistration => ({ ...store, businessEmail: store.businessEmail.trim() })

/**
 * `POST /store-applications` (público en `WebSecurityConfig.PUBLIC_ROUTES`) crea la cuenta y la
 * solicitud; `/me/store-applications` (con token) usa la cuenta con sesión. Los errores de
 * validación llegan con claves como `account.email` o `store.nit`.
 */
export function createHttpSellerApplicationService(): SellerApplicationService {
  return {
    simulated: false,

    async submit(input) {
      const request = {
        account: { ...input.account, email: input.account.email.trim() },
        store: trimStore(input.store),
        legalType: input.legalType,
      }
      const dto = await apiRequest<SellerSignupResponseDto>('/store-applications', {
        method: 'POST',
        body: multipart(request, input.documents),
      })
      return {
        email: dto.account.email,
        businessName: dto.store.businessName,
        verificationEmailSent: dto.account.verificationEmailSent,
        status: dto.applicationStatus,
        planCode: dto.planCode ?? undefined,
      }
    },

    async applyAsCurrentUser(input) {
      const request = { store: trimStore(input.store), legalType: input.legalType }
      const dto = await portalRequest<StoreApplicationCreatedDto>('/me/store-applications', {
        method: 'POST',
        body: multipart(request, input.documents),
      })
      return {
        applicationId: dto.applicationId,
        storeId: dto.storeId,
        status: dto.status,
        planCode: dto.planCode ?? undefined,
      }
    },

    async completeSetup(input) {
      const form = setupForm(input, input.token)
      const dto = await apiRequest<StoreSetupDto>('/store-applications/setup', { method: 'POST', body: form })
      return { storeId: dto.storeId, status: dto.status, trialEndsAt: dto.trialEndsAt }
    },

    async completeSetupAuthenticated(applicationId, input) {
      const dto = await portalRequest<StoreSetupDto>(`/me/store-applications/${encodeURIComponent(applicationId)}/setup`, {
        method: 'POST',
        body: setupForm(input),
      })
      return { storeId: dto.storeId, status: dto.status, trialEndsAt: dto.trialEndsAt }
    },

    async requestSetupLink(applicationId) {
      await portalRequest<void>(`/me/store-applications/${encodeURIComponent(applicationId)}/setup-link`, { method: 'POST' })
    },

    async requestSetupLinkByToken(token) {
      await apiRequest<void>('/store-applications/setup/resend', { method: 'POST', body: { token } })
    },

    async myApplications() {
      const list = await portalRequest<StoreApplicationDto[]>('/me/store-applications')
      return list.map((dto) => ({
        applicationId: dto.applicationId,
        storeId: dto.storeId,
        businessName: dto.businessName,
        status: dto.status,
        reviewNotes: dto.reviewNotes ?? undefined,
        createdAt: dto.createdAt,
      }))
    },
  }
}
