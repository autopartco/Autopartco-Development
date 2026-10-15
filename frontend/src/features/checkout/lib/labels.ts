/** Estados que devuelve el servidor, en español para mostrar. Un código desconocido se muestra tal cual. */
const STATUS_LABELS: Record<string, string> = {
  CONFIRMED: 'Confirmado',
  PENDING: 'Pendiente de confirmación por la tienda',
  APPROVED: 'Aprobado',
}

export function statusLabel(code: string): string {
  return STATUS_LABELS[code] ?? code
}

/** Fecha del servidor (`LocalDateTime` sin zona) en formato legible de Colombia. */
export function formatOrderDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeStyle: 'short' }).format(date)
}
