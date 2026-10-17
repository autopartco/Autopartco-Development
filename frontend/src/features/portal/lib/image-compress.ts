/** Límites del contrato de imágenes del producto (el servidor valida otra vez el contenido real). */
export const MAX_IMAGES = 10
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_SIDE = 1600
const WEBP_QUALITY = 0.82

export const formatMegabytes = (bytes: number) => `${(bytes / (1024 * 1024)).toLocaleString('es-CO', { maximumFractionDigits: 1 })} MB`

function toBlob(canvas: HTMLCanvasElement, type: string, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality))
}

/**
 * Reduce la imagen en el navegador: lado mayor máx. 1600 px y WebP con calidad ~0.82. Se conserva el
 * original si el navegador no sabe codificar WebP, si la lectura falla o si el resultado no es más
 * liviano. Respeta la orientación EXIF (`createImageBitmap` la aplica).
 */
export async function compressImage(file: File): Promise<File> {
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
    const width = Math.max(1, Math.round(bitmap.width * scale))
    const height = Math.max(1, Math.round(bitmap.height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) {
      bitmap.close()
      return file
    }
    context.drawImage(bitmap, 0, 0, width, height)
    bitmap.close()
    const blob = await toBlob(canvas, 'image/webp', WEBP_QUALITY)
    // Sin soporte de WebP el navegador devuelve PNG; si no ahorra peso, el original gana.
    if (!blob || blob.type !== 'image/webp' || blob.size >= file.size) return file
    return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.webp', { type: 'image/webp', lastModified: Date.now() })
  } catch {
    return file
  }
}
