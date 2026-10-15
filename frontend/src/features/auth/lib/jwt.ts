/**
 * Lee el claim `roles` del access token (JWT) emitido por `AuthTokenService`
 * (`["ROLE_CUSTOMER","ROLE_STORE_MANAGER"]` para gerentes de almacén, `["ROLE_<AUTHORITY>"]` para el resto).
 *
 * Solo decodifica el payload (base64url): NO verifica la firma. Es seguro para este uso
 * porque los roles solo deciden qué opciones muestra la UI; la autorización real la aplica
 * el servidor en cada petición con el token firmado. Nunca uses este valor para permitir
 * o negar una acción.
 */
export function rolesFromAccessToken(token: string): string[] {
  const payload = token.split('.')[1]
  if (!payload) return []
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '=')
    const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0))
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as { roles?: unknown }
    return Array.isArray(claims.roles) ? claims.roles.filter((role): role is string => typeof role === 'string') : []
  } catch {
    return []
  }
}
