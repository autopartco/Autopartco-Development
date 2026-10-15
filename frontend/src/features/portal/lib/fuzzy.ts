/** Minúsculas y sin tildes, para comparar «Reposición» con «reposicion». */
export function normalizeText(value: string): string {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

function scoreToken(token: string, text: string): number | null {
  const at = text.indexOf(token)
  if (at >= 0) {
    // Coincidencia contigua: mejor al inicio o al comienzo de una palabra.
    const wordStart = at === 0 || text[at - 1] === ' '
    return 200 - Math.min(at, 100) + (wordStart ? 60 : 0)
  }
  // Subsecuencia ("frdl" -> "freno delantero"): premia letras seguidas.
  let score = 0
  let cursor = 0
  let streak = 0
  for (const char of token) {
    const found = text.indexOf(char, cursor)
    if (found < 0) return null
    streak = found === cursor ? streak + 1 : 0
    score += 5 + streak * 3 - Math.min(found - cursor, 8)
    cursor = found + 1
  }
  return Math.max(1, score)
}

/**
 * Puntaje de coincidencia difusa (mayor es mejor) o `null` si no coincide. Cada palabra de la
 * búsqueda debe aparecer en el texto, en cualquier orden.
 */
export function fuzzyScore(query: string, text: string): number | null {
  const tokens = normalizeText(query).split(/\s+/).filter(Boolean)
  if (tokens.length === 0) return 0
  const haystack = normalizeText(text)
  let total = 0
  for (const token of tokens) {
    const score = scoreToken(token, haystack)
    if (score === null) return null
    total += score
  }
  return total
}
