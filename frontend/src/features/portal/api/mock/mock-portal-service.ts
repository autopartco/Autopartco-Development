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
      if (input.publicProfile) {
        const contact = { ...store.profile.publicProfile }
        for (const [key, value] of Object.entries(input.publicProfile)) {
          contact[key as keyof typeof contact] = value ? value : null
        }
        store.profile.publicProfile = contact
      }
    },

    async uploadBrand(storeId, asset, file) {
      await simulateNetwork(700)
      const store = getStore(storeId)
      requirePermission(store, 'STORE_UPDATE')
      const max = asset === 'logo' ? 2 : 4
      if (file.size > max * 1024 * 1024) {
        throw new ApiError(`El ${asset === 'logo' ? 'logo' : 'banner'} no puede superar ${max} MB.`, { status: 413, code: 'BRAND_IMAGE_TOO_LARGE' })
      }
      if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
        throw new ApiError('Solo se permiten imágenes PNG, JPEG o WebP.', { status: 400, code: 'UNSUPPORTED_FILE_TYPE' })
      }
      const url = URL.createObjectURL(file)
      if (asset === 'logo') store.profile.logoUrl = url
      else store.profile.bannerUrl = url
      return { ...store.profile }
    },

    async removeBrand(storeId, asset) {
      await simulateNetwork(400)
      const store = getStore(storeId)
      requirePermission(store, 'STORE_UPDATE')
      if (asset === 'logo') store.profile.logoUrl = null
      else store.profile.bannerUrl = null
    },
  }
}
