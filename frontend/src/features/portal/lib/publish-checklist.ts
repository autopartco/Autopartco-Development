export type StepId = 'producto' | 'vehiculos' | 'precio' | 'imagenes' | 'revision'
export type ChecklistState = 'ready' | 'missing' | 'warn'

export interface ChecklistItem {
  id: 'images' | 'vehicles' | 'manufacturer' | 'price' | 'description' | 'stock' | 'subscription'
  label: string
  detail?: string
  state: ChecklistState
  /** Paso donde se arregla. */
  step: StepId
}

export interface ChecklistInput {
  images: number
  rules: number
  /** Producto universal: cuenta como vehículos completos aunque no tenga reglas. */
  universal: boolean
  hasManufacturer: boolean
  price: number | undefined
  description: string
  quantity: number | undefined
  /** El servidor respondió `SUBSCRIPTION_INACTIVE` en el último intento. */
  subscriptionInactive: boolean
}

/**
 * «Qué falta para publicar», calculado en vivo con lo que hay en el borrador. Es solo una guía: el
 * servidor decide (`PRODUCT_NOT_PUBLISHABLE`) y la suscripción solo se marca cuando él lo informó.
 * Existencias en 0 es un aviso, no un bloqueo.
 */
export function buildChecklist(input: ChecklistInput): ChecklistItem[] {
  const hasVehicles = input.universal || input.rules > 0
  const items: ChecklistItem[] = [
    { id: 'images', label: 'Al menos una imagen', state: input.images > 0 ? 'ready' : 'missing', step: 'imagenes', detail: input.images > 0 ? undefined : 'Sin fotos los compradores no ven el producto.' },
    {
      id: 'vehicles',
      label: input.universal ? 'Producto universal' : 'Vehículos compatibles o universal',
      state: hasVehicles ? 'ready' : 'missing',
      step: 'vehiculos',
      detail: hasVehicles ? undefined : 'Indica los vehículos compatibles o marca el producto como universal.',
    },
    { id: 'manufacturer', label: 'Fabricante', state: input.hasManufacturer ? 'ready' : 'missing', step: 'producto' },
    { id: 'price', label: 'Precio mayor que 0', state: input.price !== undefined && input.price > 0 ? 'ready' : 'missing', step: 'precio' },
    { id: 'description', label: 'Descripción', state: input.description.trim() ? 'ready' : 'missing', step: 'producto' },
  ]
  if (input.quantity === 0) {
    items.push({ id: 'stock', label: 'Sin existencias', state: 'warn', step: 'precio', detail: 'Puedes publicar, pero aparecerá como agotado hasta que tengas unidades.' })
  }
  if (input.subscriptionInactive) {
    items.push({ id: 'subscription', label: 'Suscripción activa', state: 'warn', step: 'revision', detail: 'Tu suscripción no está activa: renuévala para publicar.' })
  }
  return items
}

export const missingCount = (items: ChecklistItem[]): number => items.filter((item) => item.state === 'missing').length

export function checklistSummary(items: ChecklistItem[]): string {
  const missing = missingCount(items)
  if (items.some((item) => item.id === 'subscription')) return 'Hay un aviso de suscripción para publicar'
  if (missing === 0) return 'Listo para publicar'
  return missing === 1 ? 'Falta 1 requisito para publicar' : `Faltan ${missing} requisitos para publicar`
}
