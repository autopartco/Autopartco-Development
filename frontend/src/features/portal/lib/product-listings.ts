/** Fila del alta multi-sucursal. `price` vacío = precio general del producto. */
export interface ListingDraft {
  key: string
  branchId: string
  /** Solo dígitos. */
  quantity: string
  /** Solo dígitos; opcional. */
  price: string
}

let draftSeq = 0
export function newListingDraft(branchId = ''): ListingDraft {
  draftSeq += 1
  return { key: `sucursal-${draftSeq}`, branchId, quantity: '', price: '' }
}

/** Primer problema de las filas del alta (mismas reglas que `listings` en `ProductCreateRequest`). */
export function listingsError(listings: ListingDraft[]): string | undefined {
  if (listings.length === 0) return 'Agrega al menos una sucursal con sus existencias.'
  const seen = new Set<string>()
  for (const [index, listing] of listings.entries()) {
    const which = listings.length > 1 ? ` (fila ${index + 1})` : ''
    if (!listing.branchId) return `Elige la sucursal${which}.`
    if (seen.has(listing.branchId)) return `Repetiste una sucursal${which}: cada sucursal va una sola vez.`
    seen.add(listing.branchId)
    if (!listing.quantity) return `Escribe cuántas unidades hay${which} (0 si no hay).`
    if (listing.price && Number(listing.price) < 1) return `El precio de la sucursal debe ser mayor que 0${which}.`
  }
  return undefined
}
