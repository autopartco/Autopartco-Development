import { ApiError } from '@/lib/api'

/** Textos para cada código `QUESTION_*` que puede devolver el servidor. */
const MESSAGES: Record<string, string> = {
  QUESTION_INVALID: 'Escribe tu pregunta con entre 10 y 300 caracteres.',
  QUESTION_CONTACT_NOT_ALLOWED: 'Quita los teléfonos, correos o enlaces: el almacén te responde aquí y te avisamos.',
  QUESTION_QUERY_INVALID: 'La búsqueda puede tener hasta 80 caracteres.',
  QUESTION_RATE_LIMITED: 'Has hecho muchas preguntas en poco tiempo. Espera un rato e inténtalo de nuevo.',
  QUESTION_ALREADY_ANSWERED: 'El almacén ya respondió esta pregunta, así que no se puede borrar. Recarga para ver la respuesta.',
  QUESTION_NOT_FOUND: 'No encontramos esa pregunta. Puede que ya la hayas borrado.',
}

/** Mensaje claro para el comprador; el texto del servidor solo se usa si no hay uno propio. */
export function questionErrorMessage(error: unknown, fallback = 'No pudimos enviar tu pregunta. Inténtalo de nuevo.'): string {
  if (error instanceof ApiError) {
    if (error.code && MESSAGES[error.code]) return MESSAGES[error.code]!
    if (error.status === 401) return 'Tu sesión venció. Ingresa de nuevo para preguntar.'
    if (error.status === 429) return MESSAGES.QUESTION_RATE_LIMITED!
    if (error.status === undefined) return 'No hay conexión con Autopartco. Revisa tu internet e inténtalo de nuevo.'
    if (error.status >= 500) return 'Autopartco tuvo un problema y no se guardó nada. Inténtalo de nuevo en unos minutos.'
    if (error.status === 400 && error.message) return error.message
  }
  return fallback
}
