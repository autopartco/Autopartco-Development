const copFormatter = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

const numberFormatter = new Intl.NumberFormat('es-CO')

/** Formatea un valor en pesos colombianos tal como llega del servidor. */
export function formatCOP(value: number): string {
  return copFormatter.format(value)
}

export function formatNumber(value: number): string {
  return numberFormatter.format(value)
}

/** "1 repuesto" / "12 repuestos". */
export function pluralize(count: number, singular: string, plural: string): string {
  return `${formatNumber(count)} ${count === 1 ? singular : plural}`
}

/** Iniciales para avatares de tiendas sin logo. */
export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter((word) => word.length > 2)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('')
}
