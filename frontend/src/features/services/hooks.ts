import { useCallback } from 'react'
import { useSearchParams } from 'react-router'
import { getMarketplaceService } from '@/features/marketplace/api/marketplace-service'
import type { MarketplaceService } from '@/features/marketplace/api/types'
import { useAsync } from '@/lib/use-async'
import type { ServiceSearchQuery } from './api/types'
import { parseServiceState, serviceQueryKey, toServiceParams, type ServiceState } from './lib/service-query'

function useMarketplace<T>(key: string, run: (service: MarketplaceService) => Promise<T>) {
  return useAsync(key, () => getMarketplaceService().then(run))
}

export function useServiceSearch(query: ServiceSearchQuery) {
  return useMarketplace(serviceQueryKey(query), (service) => service.searchServices(query))
}

export function useServiceCategories(cityCode?: string) {
  return useMarketplace(`services:categories:${cityCode ?? ''}`, (service) => service.listServiceCategories(cityCode))
}

/** Detalle de un servicio; con ubicación exacta el servidor elige la sede más cercana. */
export function useService(id: string, near?: { lat: number; lng: number }) {
  const key = `services:detail:${id}:${near ? `${near.lat},${near.lng}` : ''}`
  return useMarketplace(key, (service) => service.getService(id, near))
}

/** Parámetros de otras vistas que se conservan al cambiar un filtro (en `/buscar`, la pestaña). */
const KEPT_PARAMS = ['tab'] as const

/** Estado de la búsqueda de servicios guardado en la URL (`/servicios?...` o `/buscar?tab=services&...`). */
export function useServiceState() {
  const [searchParams, setSearchParams] = useSearchParams()
  const state = parseServiceState(searchParams)

  const update = useCallback(
    (patch: Partial<ServiceState>, options: { keepPage?: boolean } = {}) => {
      setSearchParams(
        (current) => {
          const next = { ...parseServiceState(current), ...patch }
          if (!options.keepPage) next.page = 1
          const params = toServiceParams(next)
          for (const key of KEPT_PARAMS) {
            const value = current.get(key)
            if (value) params.set(key, value)
          }
          return params
        },
        { replace: !options.keepPage, preventScrollReset: !options.keepPage },
      )
    },
    [setSearchParams],
  )

  return [state, update] as const
}
