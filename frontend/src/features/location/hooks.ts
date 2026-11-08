import { useMemo, useState, useSyncExternalStore } from 'react'
import { useAsync } from '@/lib/use-async'
import { getLocationService } from './api/location-service'
import type { LocationService, MapArea } from './api/types'
import { getSelectedCityId, subscribeSelectedCity } from './city-store'
import { DEFAULT_MAP_AREA, getMapArea, subscribeMapArea } from './location-store'

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

/** Área de mapa elegida (o Colombia completa por defecto). */
export function useMapArea(): MapArea {
  return useSyncExternalStore(subscribeMapArea, getMapArea, () => DEFAULT_MAP_AREA)
}

/**
 * Sedes para el mapa del área elegida. El nonce por montaje hace que, en zona,
 * la muestra aleatoria cambie en cada visita.
 */
export function useMapBranches() {
  const area = useMapArea()
  const [nonce] = useState(() => crypto.randomUUID())
  const { south, west, north, east } = area.bounds
  const user = area.precision === 'exact' ? area.user : undefined
  const key = `location:map:${south},${west},${north},${east}:${area.precision}:${user ? `${user.lat},${user.lng}` : '-'}:${nonce}`
  return useLocation(key, (service) => service.getMapBranches(area))
}
