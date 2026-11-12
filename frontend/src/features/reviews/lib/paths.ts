/** Pantalla de reseñas pendientes; el correo de invitación enlaza aquí con `?pedido=`. */
export function ratePath(orderId?: string): string {
  return orderId ? `/cuenta/calificar?pedido=${encodeURIComponent(orderId)}` : '/cuenta/calificar'
}
