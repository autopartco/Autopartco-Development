import { useMemo, useState, useSyncExternalStore } from 'react'
import { useAsync } from '@/lib/use-async'
import { getLocationService } from './api/location-service'
import type { LocationService, MapArea } from './api/types'
import { DEFAULT_MAP_AREA, getMapArea, subscribeMapArea } from './location-store'

function useLocation<T>(key: string, run: (service: LocationService) => Promise<T>) {
  return useAsync(key, () => getLocationService().then(run))
}

/** Capacidades y ciudad por defecto del servicio de ubicación. */
export function useLocationService(): LocationService | undefined {
  return useLocation('location:service', async (service) => service).data
}

export function useCities() {
  return useLocation('location:cities', (service) => service.listCities())
}

/** Área de mapa elegida (o Colombia completa por defecto). */
export function useMapArea(): MapArea {
  return useSyncExternalStore(subscribeMapArea, getMapArea, () => DEFAULT_MAP_AREA)
}

const NO_DISTANCES: ReadonlyMap<string, number> = new Map()

/**
 * Distancia a la sede más cercana de cada tienda desde la ubicación exacta del usuario.
 * Vacío en zona: la UI no muestra kilómetros. La clave es estable para compartir la petición.
 */
export function useStoreDistances(): ReadonlyMap<string, number> {
  const area = useMapArea()
  const user = area.precision === 'exact' ? area.user : undefined
  const key = user ? `location:distances:${user.lat},${user.lng}` : 'location:distances:-'
  const branches = useLocation(key, async (service) => (user ? service.getMapBranches(area) : [])).data
  return useMemo(() => {
    if (!user || !branches) return NO_DISTANCES
    const distances = new Map<string, number>()
    for (const branch of branches) {
      if (branch.distanceKm === undefined) continue
      const current = distances.get(branch.storeId)
      if (current === undefined || branch.distanceKm < current) distances.set(branch.storeId, branch.distanceKm)
    }
    return distances
  }, [branches, user])
}

/** Origen de las distancias: solo hay con ubicación exacta. */
export function useDistanceOrigin(): string | undefined {
  const area = useMapArea()
  return area.precision === 'exact' && area.user ? 'tu ubicación' : undefined
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
