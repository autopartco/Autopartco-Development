import { safeReturnPath } from '@/features/auth/lib/return-path'

/**
 * Ruta interna a la que lleva la notificación. Solo se aceptan rutas que empiezan por `/` (y no `//`, esquemas ni
 * rutas de otro origen): cualquier otra cosa se ignora y la notificación queda sin enlace.
 */
export function internalLink(link: string | null): string | undefined {
  if (!link) return undefined
  const safe = safeReturnPath(link, '')
  return safe || undefined
}
