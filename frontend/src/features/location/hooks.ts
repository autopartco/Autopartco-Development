import { useMemo, useSyncExternalStore } from 'react'
import { useAsync } from '@/lib/use-async'
import { getLocationService } from './api/location-service'
import type { LocationService } from './api/types'
import { getSelectedCityId, subscribeSelectedCity } from './city-store'

function useLocation<T>(key: string, run: (service: LocationService) => Promise<T>) {
  return useAsync(key, () => getLocationService().then(run))
}

/** Capacidades y ciudad por defecto del servicio de ubicación. */
export function useLocationService(): LocationService | undefined {
  return useLocation('location:service', async (service) => service).data
}

/** Ciudad elegida explícitamente por el usuario (sin valor: la del servicio). */
export function useSelectedCityId(): string | undefined {
  return useSyncExternalStore(subscribeSelectedCity, getSelectedCityId, () => undefined)
}

export function useCities() {
  return useLocation('location:cities', (service) => service.listCities())
}

/** Almacenes con sede en la ciudad elegida, del más cercano al más lejano. */
export function useNearbyStores() {
  const cityId = useSelectedCityId()
  return useLocation(`location:nearby:${cityId ?? 'default'}`, (service) => service.getNearbyStores(cityId))
}

/**
 * Distancia a la sede más cercana de cada tienda en la ciudad elegida.
 * Vacío si el servicio no informa distancias: la UI no muestra kilómetros.
 */
export function useStoreDistances(): ReadonlyMap<string, number> {
  const nearby = useNearbyStores().data
  return useMemo(() => {
    const distances = new Map<string, number>()
    for (const entry of nearby?.stores ?? []) {
      if (entry.distanceKm === undefined) continue
      const current = distances.get(entry.store.id)
      if (current === undefined || entry.distanceKm < current) distances.set(entry.store.id, entry.distanceKm)
    }
    return distances
  }, [nearby])
}
