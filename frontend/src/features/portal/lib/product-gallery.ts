import type { ProductImage } from '../api/types'

/** Imagen ya guardada en el servidor o archivo local que viaja con el envío final. */
export type GalleryItem =
  | { key: string; kind: 'existing'; image: ProductImage }
  | { key: string; kind: 'new'; file: File; previewUrl: string }

export const galleryFrom = (images: ProductImage[]): GalleryItem[] =>
  images.toSorted((a, b) => a.displayOrder - b.displayOrder).map((image) => ({ key: image.id, kind: 'existing', image }))

export const mainKeyFrom = (images: ProductImage[]): string => (images.find((image) => image.isMain) ?? images.toSorted((a, b) => a.displayOrder - b.displayOrder)[0])?.id ?? ''

export const thumbOf = (item: GalleryItem): string => (item.kind === 'existing' ? item.image.thumbUrl : item.previewUrl)

export const newFiles = (gallery: GalleryItem[]): File[] => gallery.flatMap((item) => (item.kind === 'new' ? [item.file] : []))

export function releasePreviews(gallery: GalleryItem[]): void {
  for (const item of gallery) if (item.kind === 'new') URL.revokeObjectURL(item.previewUrl)
}

/** Garantiza una principal válida: la elegida si sigue en la galería, o la primera. */
export const resolveMainKey = (gallery: GalleryItem[], mainKey: string): string =>
  gallery.some((item) => item.key === mainKey) ? mainKey : (gallery[0]?.key ?? '')

/** Alta: archivos en orden de galería y posición de la principal. */
export function createImagePart(gallery: GalleryItem[], mainKey: string): { files: File[]; mainImageIndex?: number } {
  const files = newFiles(gallery)
  const main = resolveMainKey(gallery, mainKey)
  const index = gallery.findIndex((item) => item.key === main)
  return { files, mainImageIndex: index >= 0 ? index : undefined }
}

/** Edición: `removeImageIds`, `imageOrder` (ids y `new:<i>`) y `mainImage`; vacío si la galería no cambió. */
export function editImagePart(
  initial: ProductImage[],
  initialMainKey: string,
  gallery: GalleryItem[],
  mainKey: string,
): { files: File[]; removeImageIds?: string[]; imageOrder?: string[]; mainImage?: string } {
  const files = newFiles(gallery)
  let newIndex = 0
  const refs = gallery.map((item) => (item.kind === 'existing' ? item.image.id : `new:${newIndex++}`))
  const keptIds = new Set(gallery.flatMap((item) => (item.kind === 'existing' ? [item.image.id] : [])))
  const removeImageIds = initial.filter((image) => !keptIds.has(image.id)).map((image) => image.id)
  const initialOrder = initial.toSorted((a, b) => a.displayOrder - b.displayOrder).map((image) => image.id)
  const main = resolveMainKey(gallery, mainKey)
  const mainRef = refs[gallery.findIndex((item) => item.key === main)]
  const orderChanged = refs.length !== initialOrder.length || refs.some((ref, index) => ref !== initialOrder[index])
  const mainChanged = (mainRef ?? '') !== initialMainKey
  return {
    files,
    ...(removeImageIds.length > 0 ? { removeImageIds } : {}),
    ...(orderChanged ? { imageOrder: refs } : {}),
    ...(mainChanged && mainRef ? { mainImage: mainRef } : {}),
  }
}

export const galleryChanged = (part: ReturnType<typeof editImagePart>): boolean =>
  part.files.length > 0 || part.removeImageIds !== undefined || part.imageOrder !== undefined || part.mainImage !== undefined
