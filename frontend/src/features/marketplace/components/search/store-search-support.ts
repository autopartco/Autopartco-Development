import { useMemo } from 'react'
import { useServiceSearch } from '@/features/services/hooks'
import { parseServiceState, toServiceQuery } from '@/features/services/lib/service-query'
import { MarketplaceError } from '../../api/types'
import { useListingSearch } from '../../hooks'
import { parseSearchState, toSearchQuery } from '../../lib/search-query'

export function errorCopy(error: unknown): { title: string; description: string } {
  if (error instanceof MarketplaceError) {
    if (error.code === 'SEARCH_RATE_LIMITED' || error.status === 429) {
      return { title: 'Estás buscando muy rápido', description: 'Espera unos segundos y vuelve a intentarlo. Tu búsqueda se mantiene.' }
    }
    if (error.code?.startsWith('SEARCH_')) {
      return {
        title: 'No pudimos interpretar la búsqueda',
        description: 'Revisa el texto o la ciudad elegida, o quita el filtro de ciudad, e inténtalo de nuevo.',
      }
    }
  }
  return {
    title: 'No pudimos cargar las tiendas',
    description: 'El listado no respondió. Revisa tu conexión e inténtalo de nuevo; tu búsqueda se mantiene.',
  }
}

/**
 * Ciudades para el filtro. El buscador de tiendas no devuelve facetas, así que se ofrecen las ciudades con repuestos o
 * servicios publicados (facetas del catálogo completo, sin texto), con el mismo código DIVIPOLA que acepta el servidor.
 */
export function useCityOptions(selected: string | undefined) {
  const parts = useListingSearch(
    useMemo(() => ({ ...toSearchQuery({ ...parseSearchState(new URLSearchParams()), pageSize: 1 }, undefined) }), []),
  )
  const services = useServiceSearch(
    useMemo(() => ({ ...toServiceQuery(parseServiceState(new URLSearchParams()), undefined), pageSize: 1 }), []),
  )
  return useMemo(() => {
    const byCode = new Map<string, string>()
    for (const city of [...(parts.data?.facets?.cities ?? []), ...(services.data?.facets?.cities ?? [])]) {
      if (!byCode.has(city.code)) byCode.set(city.code, city.name)
    }
    if (selected && !byCode.has(selected)) byCode.set(selected, 'Ciudad elegida')
    return [...byCode].map(([code, name]) => ({ code, name })).toSorted((a, b) => a.name.localeCompare(b.name, 'es'))
  }, [parts.data, services.data, selected])
}
