/** Texto de cada puntaje; el 1 es el peor y el 5 el mejor. */
export const RATING_WORDS = ['Muy malo', 'Malo', 'Regular', 'Bueno', 'Excelente'] as const

export function ratingWord(value: number): string | undefined {
  return RATING_WORDS[value - 1]
}
