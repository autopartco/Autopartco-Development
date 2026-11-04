import { useEffect, useRef, useState, type DragEvent } from 'react'
import { ChevronLeftIcon, ChevronRightIcon, ImagePlusIcon, LoaderCircleIcon, StarIcon, Trash2Icon } from 'lucide-react'
import { SmartImage } from '@/components/common/smart-image'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { ACCEPTED_IMAGE_TYPES, compressImage, formatMegabytes, MAX_IMAGE_BYTES, MAX_IMAGES } from '../lib/image-compress'
import { releasePreviews, resolveMainKey, thumbOf, type GalleryItem } from '../lib/product-gallery'

interface ProductGalleryEditorProps {
  id: string
  items: GalleryItem[]
  mainKey: string
  onChange: (items: GalleryItem[], mainKey: string) => void
  productName: string
  error?: string
  disabled?: boolean
}

let counter = 0
const nextKey = () => `new-${(counter += 1)}`

/**
 * Galería local del producto: nada se sube hasta el envío final. Las imágenes nuevas se previsualizan
 * con `URL.createObjectURL` y se reducen en el navegador (WebP, máx. 1600 px). Se reordenan con
 * botones (accesibles por teclado), se marca la principal y se quitan sin tocar el servidor.
 */
export function ProductGalleryEditor({ id, items, mainKey, onChange, productName, error, disabled }: ProductGalleryEditorProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [notices, setNotices] = useState<string[]>([])
  const [announcement, setAnnouncement] = useState('')
  const pendingFocus = useRef<string[]>([])

  const latest = useRef({ items, mainKey, onChange })
  useEffect(() => {
    latest.current = { items, mainKey, onChange }
  })
  // Las URL de previsualización las libera quien guarda la galería (la página) al salir del formulario:
  // este componente se desmonta al cambiar de paso y las miniaturas deben seguir vivas.

  // Tras mover o quitar, el foco vuelve al control usado (el DOM se reordena).
  useEffect(() => {
    const target = pendingFocus.current.map((candidate) => document.getElementById(candidate)).find((element) => element instanceof HTMLButtonElement && !element.disabled)
    pendingFocus.current = []
    target?.focus()
  }, [items])

  const main = resolveMainKey(items, mainKey)
  const full = items.length >= MAX_IMAGES

  async function addFiles(list: File[]) {
    if (list.length === 0 || disabled) return
    const problems: string[] = []
    const room = MAX_IMAGES - latest.current.items.length
    const valid = list.filter((file) => {
      if (ACCEPTED_IMAGE_TYPES.includes(file.type)) return true
      problems.push(`“${file.name}” no es una imagen JPG, PNG o WebP y se omitió.`)
      return false
    })
    if (room <= 0) problems.push(`Ya tienes ${MAX_IMAGES} imágenes, el máximo. Quita alguna para agregar otra.`)
    else if (valid.length > room) problems.push(`Solo caben ${room} imagen${room === 1 ? '' : 'es'} más (máximo ${MAX_IMAGES}). Se omitieron ${valid.length - room}.`)
    const picked = valid.slice(0, Math.max(room, 0))
    setNotices(problems)
    if (picked.length === 0) return
    setBusy(true)
    const added: GalleryItem[] = []
    for (const original of picked) {
      const file = await compressImage(original)
      if (file.size > MAX_IMAGE_BYTES) {
        problems.push(`“${original.name}” pesa ${formatMegabytes(file.size)} incluso optimizada; el máximo es 5 MB. Elige una más liviana.`)
        continue
      }
      added.push({ key: nextKey(), kind: 'new', file, previewUrl: URL.createObjectURL(file) })
    }
    setBusy(false)
    setNotices([...problems])
    if (added.length === 0) return
    const current = latest.current
    const space = Math.max(MAX_IMAGES - current.items.length, 0)
    releasePreviews(added.slice(space))
    const kept = added.slice(0, space)
    const next = [...current.items, ...kept]
    current.onChange(next, resolveMainKey(next, current.mainKey))
    setAnnouncement(`${kept.length} imagen${kept.length === 1 ? '' : 'es'} agregada${kept.length === 1 ? '' : 's'}. Hay ${next.length} en total.`)
  }

  function move(index: number, delta: -1 | 1) {
    const target = index + delta
    if (target < 0 || target >= items.length) return
    const next = items.slice()
    const [moved] = next.splice(index, 1)
    if (!moved) return
    next.splice(target, 0, moved)
    onChange(next, main)
    pendingFocus.current = [`${id}-${moved.key}-${delta < 0 ? 'antes' : 'despues'}`, `${id}-${moved.key}-${delta < 0 ? 'despues' : 'antes'}`]
    setAnnouncement(`Imagen movida a la posición ${target + 1} de ${items.length}.`)
  }

  function makeMain(item: GalleryItem, index: number) {
    onChange(items, item.key)
    setAnnouncement(`La imagen ${index + 1} es ahora la principal.`)
  }

  function remove(item: GalleryItem, index: number) {
    if (item.kind === 'new') URL.revokeObjectURL(item.previewUrl)
    const next = items.filter((candidate) => candidate.key !== item.key)
    onChange(next, resolveMainKey(next, main === item.key ? '' : main))
    pendingFocus.current = [`${id}-agregar`]
    setAnnouncement(`Imagen ${index + 1} quitada. Quedan ${next.length}.`)
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setDragging(false)
    void addFiles([...event.dataTransfer.files])
  }

  const errorId = error ? `${id}-error` : undefined

  return (
    <div className="flex flex-col gap-4">
      <div
        onDragOver={(event) => {
          event.preventDefault()
          if (!disabled) setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={cn(
          'flex flex-col items-center gap-3 rounded-xl border-2 border-dashed p-6 text-center transition-colors',
          dragging ? 'border-primary bg-primary/5' : 'border-border bg-card',
          error && 'border-destructive',
        )}
      >
        <ImagePlusIcon className="size-8 text-muted-foreground" aria-hidden />
        <p className="text-sm text-muted-foreground">
          Arrastra tus fotos aquí o elígelas desde tu dispositivo.
          <br />
          JPG, PNG o WebP, hasta {MAX_IMAGES} imágenes de 5 MB. Las reducimos automáticamente para que carguen rápido.
        </p>
        <input
          ref={inputRef}
          id={`${id}-archivos`}
          type="file"
          accept={ACCEPTED_IMAGE_TYPES.join(',')}
          multiple
          className="sr-only"
          tabIndex={-1}
          aria-hidden
          disabled={disabled || busy || full}
          onChange={(event) => {
            void addFiles([...(event.target.files ?? [])])
            event.target.value = ''
          }}
        />
        <Button
          id={`${id}-agregar`}
          type="button"
          variant="outline"
          className="h-11 px-4"
          disabled={disabled || busy || full}
          aria-describedby={errorId}
          aria-invalid={error ? true : undefined}
          onClick={() => inputRef.current?.click()}
        >
          {busy ? <LoaderCircleIcon className="animate-spin" aria-hidden /> : <ImagePlusIcon aria-hidden />}
          {busy ? 'Optimizando…' : 'Agregar imágenes'}
        </Button>
        {full ? <p className="text-sm text-muted-foreground">Llegaste al máximo de {MAX_IMAGES} imágenes.</p> : null}
      </div>

      {error ? (
        <p id={errorId} role="alert" className="text-sm font-medium text-destructive">
          {error}
        </p>
      ) : null}
      {notices.length > 0 ? (
        <ul role="alert" className="flex flex-col gap-1 rounded-lg border border-stock-low/30 bg-stock-low/10 p-3 text-sm text-foreground">
          {notices.map((notice) => (
            <li key={notice}>{notice}</li>
          ))}
        </ul>
      ) : null}
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {items.length === 0 ? (
        <p className="rounded-lg bg-concrete p-4 text-sm text-muted-foreground">Aún no hay imágenes. Para publicar necesitas al menos una; una buena foto ayuda a vender más.</p>
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((item, index) => {
            const isMain = item.key === main
            return (
              <li key={item.key} className={cn('flex flex-col gap-2 rounded-lg bg-card p-2 ring-1 ring-foreground/10', isMain && 'ring-2 ring-primary')}>
                <SmartImage
                  src={thumbOf(item)}
                  alt={`${productName || 'Producto'}, imagen ${index + 1}${isMain ? ' (principal)' : ''}`}
                  width={240}
                  height={240}
                  frameClassName="aspect-square w-full rounded-md"
                />
                <div className="flex min-h-7 flex-wrap items-center gap-1.5 text-xs font-semibold">
                  <span className="text-muted-foreground">{index + 1} de {items.length}</span>
                  {isMain ? (
                    <span className="inline-flex items-center gap-1 text-recommended">
                      <StarIcon className="size-3.5 fill-current" aria-hidden />
                      Principal
                    </span>
                  ) : null}
                  {item.kind === 'new' ? <span className="text-muted-foreground">· Nueva</span> : null}
                </div>
                <div className="flex flex-wrap gap-1">
                  <Button
                    id={`${id}-${item.key}-antes`}
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-9 flex-1 pointer-coarse:h-11"
                    disabled={disabled || index === 0}
                    onClick={() => move(index, -1)}
                    aria-label={`Mover la imagen ${index + 1} antes`}
                  >
                    <ChevronLeftIcon aria-hidden />
                  </Button>
                  <Button
                    id={`${id}-${item.key}-despues`}
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-9 flex-1 pointer-coarse:h-11"
                    disabled={disabled || index === items.length - 1}
                    onClick={() => move(index, 1)}
                    aria-label={`Mover la imagen ${index + 1} después`}
                  >
                    <ChevronRightIcon aria-hidden />
                  </Button>
                  {isMain ? null : (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="h-9 flex-1 pointer-coarse:h-11"
                      disabled={disabled}
                      onClick={() => makeMain(item, index)}
                      aria-label={`Hacer principal la imagen ${index + 1}`}
                    >
                      <StarIcon aria-hidden />
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="destructive"
                    size="sm"
                    className="h-9 flex-1 pointer-coarse:h-11"
                    disabled={disabled}
                    onClick={() => remove(item, index)}
                    aria-label={`Quitar la imagen ${index + 1}`}
                  >
                    <Trash2Icon aria-hidden />
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
