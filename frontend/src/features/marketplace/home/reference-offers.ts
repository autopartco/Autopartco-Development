import { useMemo } from 'react'
import type { Brand, Product } from '../api/types'
import { useProductSearch } from '../hooks'
import { emptyQuery } from '../lib/product-query'

/**
 * Una misma referencia (marca + número de parte) publicada por varios almacenes.
 * Sirve al pasaporte y a la comparación de precios; ambos comparten la misma
 * consulta (y la caché de `useAsync`).
 */
export interface ReferenceOffers {
  partNumber: string
  name: string
  brand?: Brand
  offers: Product[]
}

const CATALOG_SAMPLE = emptyQuery({ pageSize: 60 })

function inStock(product: Product): boolean {
  return product.availability?.status !== 'out_of_stock'
}

/** La referencia con más ofertas; en empate, la que tiene más ofertas con existencias. */
export function mostOfferedReference(products: Product[]): ReferenceOffers | undefined {
  const groups = new Map<string, Product[]>()
  for (const product of products) {
    if (!product.partNumber) continue
    const key = `${product.brand?.id ?? ''}|${product.partNumber}`
    const group = groups.get(key)
    if (group) group.push(product)
    else groups.set(key, [product])
  }
  let best: Product[] | undefined
  for (const group of groups.values()) {
    if (
      !best ||
      group.length > best.length ||
      (group.length === best.length && group.filter(inStock).length > best.filter(inStock).length)
    ) {
      best = group
    }
  }
  const first = best?.[0]
  if (!best || !first?.partNumber) return undefined
  return { partNumber: first.partNumber, name: first.name, brand: first.brand, offers: best }
}

/** Oferta destacada: la más cercana con existencias; si no hay distancias, la más barata con existencias. */
export function featuredOffer(offers: Product[], distances: ReadonlyMap<string, number>): Product | undefined {
  const available = offers.filter(inStock)
  const pool = available.length > 0 ? available : offers
  const distanceOf = (product: Product) => (product.store ? distances.get(product.store.id) : undefined)
  const withDistance = pool.filter((product) => distanceOf(product) !== undefined)
  if (withDistance.length > 0) {
    return withDistance.reduce((best, product) => ((distanceOf(product) ?? 0) < (distanceOf(best) ?? 0) ? product : best))
  }
  return pool.reduce<Product | undefined>((best, product) => (!best || product.price < best.price ? product : best), undefined)
}

export function useMostOfferedReference() {
  const result = useProductSearch(CATALOG_SAMPLE)
  const items = result.data?.items
  const reference = useMemo(() => (items ? mostOfferedReference(items) : undefined), [items])
  return { status: result.status, loading: !items, reference, retry: result.retry }
}
