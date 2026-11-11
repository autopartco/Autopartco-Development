/** Palabras de códigos del catálogo que llevan tilde al mostrarse. */
const ACCENTED_WORDS: Record<string, string> = {
  carroceria: 'carrocería',
  condicion: 'condición',
  direccion: 'dirección',
  electrico: 'eléctrico',
  garantia: 'garantía',
  iluminacion: 'iluminación',
  refrigeracion: 'refrigeración',
  suspension: 'suspensión',
  transmision: 'transmisión',
}

/** "FRENOS_Y_DISCOS" -> "Frenos y discos"; "garantia_meses" -> "Garantía meses". */
export function humanizeCode(code: string): string {
  const words = code
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase()
    .split(' ')
    .map((word) => ACCENTED_WORDS[word] ?? word)
    .join(' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}
