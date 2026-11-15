import { DATA_SOURCE } from '@/lib/api'

/** `core/settings/StoreSettings` (los apartados que usa esta pantalla; el servidor devuelve el objeto completo). */
export interface StoreSettings {
  contact: { whatsappEnabled: boolean; whatsappMessage: string | null; whatsappServiceMessage: string | null }
  notifications: {
    newOrder: boolean
    lowStock: boolean
    newReview: boolean
    weeklyReport: boolean
    questions: boolean
    newBooking: boolean
    recipients: string[]
  }
}

/** Mensaje por defecto del servidor (`SettingsSchemas.DEFAULT_WHATSAPP_SERVICE_MESSAGE`). */
export const DEFAULT_WHATSAPP_SERVICE_MESSAGE = 'Hola, vi el servicio {servicio} en Autopartco y quiero información para agendar: {enlace}'

/** Parche parcial: solo las hojas presentes cambian. */
export interface StoreSettingsPatch {
  contact?: { whatsappServiceMessage?: string }
  notifications?: { newBooking?: boolean }
}

/** `storesuite/store/StoreSettingsController` (`GET`/`PATCH /stores/{id}/settings`, permiso `STORE_UPDATE`). */
export interface StoreSettingsService {
  get(storeId: string): Promise<StoreSettings>
  /** 400 `INVALID_SETTING` con `details.key` = ruta con puntos (p. ej. `contact.whatsappServiceMessage`). */
  patch(storeId: string, patch: StoreSettingsPatch): Promise<StoreSettings>
}

let servicePromise: Promise<StoreSettingsService> | undefined

export function getStoreSettingsService(): Promise<StoreSettingsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-store-settings-service').then((module) => module.createHttpStoreSettingsService())
      : import('./mock/mock-store-settings-service').then((module) => module.createMockStoreSettingsService())
  return servicePromise
}
