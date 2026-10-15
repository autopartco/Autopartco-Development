/**
 * Destino de vuelta tras iniciar sesión (`?next=`). Solo se aceptan rutas internas de la
 * misma aplicación: cualquier otra cosa (URL absoluta, `//host`, `/\host`, esquemas como
 * `javascript:`) se descarta para evitar redirecciones abiertas.
 */
export function safeReturnPath(value: unknown, fallback = '/'): string {
  if (typeof value !== 'string' || value.length === 0 || value.length > 2000) return fallback
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return fallback
  // Caracteres de control o espacios iniciales que algunos navegadores normalizan.
  for (const char of value) {
    const code = char.codePointAt(0) ?? 0
    if (code <= 0x1f || code === 0x7f) return fallback
  }
  try {
    const base = window.location.origin
    const url = new URL(value, base)
    if (url.origin !== base) return fallback
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return fallback
  }
}

/** Ruta de inicio de sesión que conserva el destino actual (sin datos personales: solo ruta y consulta). */
export function loginPathFor(destination: string): string {
  const safe = safeReturnPath(destination, '')
  return safe ? `/ingresar?next=${encodeURIComponent(safe)}` : '/ingresar'
}
