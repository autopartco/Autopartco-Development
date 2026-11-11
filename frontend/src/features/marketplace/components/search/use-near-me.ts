import { useState } from 'react'
import { useMapArea } from '@/features/location/hooks'
import { locateMe } from '@/features/location/lib/locate-me'
import type { GeoPoint } from '@/features/location/api/types'

const MESSAGES = {
  unsupported: 'Tu navegador no permite usar la ubicación. Elige una ciudad en los filtros.',
  denied: 'No pudimos obtener tu ubicación. Revisa el permiso del navegador e inténtalo de nuevo.',
  outside: 'Solo mostramos almacenes en Colombia.',
} as const

/**
 * «Cerca de mí»: usa la ubicación exacta ya elegida en el área del mapa (`useMapArea`) o la pide al
 * navegador, siempre con el permiso de la persona. Las coordenadas viven en memoria; no van a la URL.
 */
export function useNearMe() {
  const area = useMapArea()
  const origin: GeoPoint | undefined = area.precision === 'exact' ? area.user : undefined
  const [locating, setLocating] = useState(false)
  const [error, setError] = useState<string>()

  async function request(): Promise<boolean> {
    if (origin) return true
    setLocating(true)
    setError(undefined)
    try {
      const result = await locateMe()
      if (!result.ok) setError(MESSAGES[result.reason])
      return result.ok
    } finally {
      setLocating(false)
    }
  }

  return { origin, locating, error, request }
}

export type NearMe = ReturnType<typeof useNearMe>
