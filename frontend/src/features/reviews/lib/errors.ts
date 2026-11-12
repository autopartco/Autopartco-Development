import { ApiError } from '@/lib/api'

/** Textos para cada código `REVIEW_*` que puede devolver el servidor al calificar, editar o borrar. */
const MESSAGES: Record<string, string> = {
  REVIEW_NOT_ELIGIBLE: 'Solo puedes calificar un repuesto que ya recibiste. Cuando tu pedido llegue, vuelve a intentarlo.',
  REVIEW_NOT_ALLOWED: 'No puedes calificar esta compra. Solo califican quienes recibieron un pedido de la tienda.',
  REVIEW_ALREADY_EXISTS: 'Ya calificaste esto. Puedes editar tu reseña desde el detalle del pedido.',
  REVIEW_EDIT_WINDOW_CLOSED: 'Ya pasaron los 30 días para editar esta reseña. Si quieres, puedes borrarla.',
  REVIEW_LINKS_NOT_ALLOWED: 'Tu comentario no puede incluir enlaces. Quítalos y vuelve a publicarlo.',
  REVIEW_ASPECT_INVALID: 'Una de las categorías no es válida. Recarga la página e inténtalo de nuevo.',
  REVIEW_RATE_LIMITED: 'Has enviado muchas reseñas en poco tiempo. Espera un rato e inténtalo de nuevo.',
  REVIEW_NOT_FOUND: 'No encontramos esa reseña. Puede que ya la hayas borrado.',
  REVIEW_MODERATED: 'Esta reseña fue retirada por moderación y no se puede publicar de nuevo.',
  ORDER_NOT_FOUND: 'No encontramos ese pedido en tu cuenta.',
}

/** Mensaje claro para el comprador; el texto del servidor solo se usa si no hay uno propio. */
export function reviewErrorMessage(error: unknown, fallback = 'No pudimos guardar tu reseña. Inténtalo de nuevo.'): string {
  if (error instanceof ApiError) {
    if (error.code && MESSAGES[error.code]) return MESSAGES[error.code]!
    if (error.status === 401) return 'Tu sesión venció. Ingresa de nuevo para calificar.'
    if (error.status === 429) return MESSAGES.REVIEW_RATE_LIMITED!
    if (error.status === undefined) return 'No hay conexión con Autopartco. Revisa tu internet e inténtalo de nuevo.'
    if (error.status >= 500) return 'Autopartco tuvo un problema y no se guardó nada. Inténtalo de nuevo en unos minutos.'
    if (error.status === 400 && error.message) return error.message
  }
  return fallback
}

/** `true` si la ventana de 30 días para editar ya venció (`editableUntil` ausente = sin dato, se intenta). */
export function editWindowClosed(editableUntil: string | undefined, now = Date.now()): boolean {
  if (!editableUntil) return false
  const limit = new Date(editableUntil).getTime()
  return !Number.isNaN(limit) && limit < now
}
