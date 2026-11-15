import { ApiError } from '@/lib/api'
import { DEFAULT_WHATSAPP_SERVICE_MESSAGE, type StoreSettings, type StoreSettingsService } from '../store-settings-service'
import { getStore, requirePermission, simulateNetwork } from './mock-state'

const byStore = new Map<string, StoreSettings>()

const settingsOf = (storeId: string): StoreSettings => {
  if (!byStore.has(storeId)) {
    byStore.set(storeId, {
      contact: { whatsappEnabled: true, whatsappMessage: null, whatsappServiceMessage: DEFAULT_WHATSAPP_SERVICE_MESSAGE },
      notifications: { newOrder: true, lowStock: true, newReview: true, weeklyReport: false, questions: true, newBooking: true, recipients: [] },
    })
  }
  return byStore.get(storeId)!
}

export function createMockStoreSettingsService(): StoreSettingsService {
  return {
    async get(storeId) {
      await simulateNetwork()
      requirePermission(getStore(storeId), 'STORE_UPDATE')
      return structuredClone(settingsOf(storeId))
    },
    async patch(storeId, patch) {
      await simulateNetwork(500)
      requirePermission(getStore(storeId), 'STORE_UPDATE')
      const current = settingsOf(storeId)
      const message = patch.contact?.whatsappServiceMessage
      if (message !== undefined) {
        if (message.length > 300 || !message.includes('{enlace}')) {
          throw new ApiError('El mensaje de WhatsApp debe incluir {enlace}.', { status: 400, code: 'INVALID_SETTING', details: { key: 'contact.whatsappServiceMessage' } })
        }
        current.contact.whatsappServiceMessage = message
      }
      if (patch.notifications?.newBooking !== undefined) current.notifications.newBooking = patch.notifications.newBooking
      return structuredClone(current)
    },
  }
}
