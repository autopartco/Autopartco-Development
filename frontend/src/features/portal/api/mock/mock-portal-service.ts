import { ApiError } from '@/lib/api'
import type { PortalService } from '../portal-service'
import { getStore, requirePermission, simulateNetwork, storesForSession, summaryOf } from './mock-state'

/** Tiendas, permisos y perfil simulados (ver `mock-state.ts` para los casos de prueba). */
export function createMockPortalService(): PortalService {
  return {
    async myStores() {
      await simulateNetwork()
      return storesForSession().map(summaryOf)
    },

    async permissions(storeId) {
      await simulateNetwork(250)
      return [...getStore(storeId).permissions]
    },

    async profile(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'STORE_READ')
      return { ...store.profile }
    },

    async updateProfile(storeId, input) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'STORE_UPDATE')
      if (input.businessEmail !== undefined && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.businessEmail)) {
        throw new ApiError('Hay datos por corregir.', {
          status: 400,
          code: 'VALIDATION_ERROR',
          fieldErrors: { businessEmail: 'El correo del almacén debe tener un formato válido.' },
        })
      }
      if (input.businessName !== undefined) {
        store.profile.businessName = input.businessName
        store.summary.businessName = input.businessName
      }
      if (input.businessEmail !== undefined) store.profile.businessEmail = input.businessEmail
      if (input.description !== undefined) store.profile.description = input.description || null
    },
  }
}
