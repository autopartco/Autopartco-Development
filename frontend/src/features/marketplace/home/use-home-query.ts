import { useMemo } from 'react'
import { useMapArea } from '@/features/location/hooks'
import type { HomeQuery } from '../api/types'
import { useHome } from '../hooks'

/** Tres decimales (~110 m): estable para la caché y sin exponer más precisión de la necesaria. */
const round = (value: number) => Math.round(value * 1000) / 1000

/**
 * Área del inicio a partir del selector de la cabecera: con ubicación exacta usa el punto del usuario; con una
 * zona elegida, su centro; con «Colombia» no envía punto. Nunca pide geolocalización al cargar.
 */
export function useHomeQuery(): HomeQuery {
  const area = useMapArea()
  const point = area.precision === 'exact' && area.user ? area.user : area.label === 'Colombia' ? undefined : area.center
  const lat = point ? round(point.lat) : undefined
  const lng = point ? round(point.lng) : undefined
  return useMemo(() => ({ lat, lng }), [lat, lng])
}

/** Datos del inicio para el área elegida. */
export function useHomeData() {
  return useHome(useHomeQuery())
}
