import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react'
import { ImageIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { invalidateAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { getPortalService } from '../api/portal-service'
import type { StoreBrandAsset } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { ConfirmDialog } from './confirm-dialog'

const ACCEPTED = ['image/png', 'image/jpeg', 'image/webp']

const COPY: Record<StoreBrandAsset, { label: string; maxMb: number; hint: string }> = {
  logo: { label: 'Logo', maxMb: 2, hint: 'Cuadrado, mínimo 256 × 256 px. PNG, JPEG o WebP hasta 2 MB.' },
  banner: { label: 'Banner', maxMb: 4, hint: 'Horizontal, se recorta a 5:1 en computador y 3:1 en celular. PNG, JPEG o WebP hasta 4 MB.' },
}

interface BrandUploaderProps {
  asset: StoreBrandAsset
  /** URL pública actual; solo carga si la tienda está publicada. */
  url: string | null
  editable: boolean
  published: boolean
}

/**
 * Logo o banner del almacén. Se guarda al elegir el archivo (no depende de «Guardar cambios»): sube, reemplaza el
 * anterior y recarga el perfil. La vista previa usa el archivo local, porque la URL pública solo responde cuando la
 * tienda está publicada.
 */
export function BrandUploader({ asset, url, editable, published }: BrandUploaderProps) {
  const { storeId } = usePortal()
  const inputId = useId()
  const hintId = `${inputId}-ayuda`
  const errorId = `${inputId}-error`
  const input = useRef<HTMLInputElement>(null)
  const [preview, setPreview] = useState<string>()
  // URL que falló al cargar (p. ej. tienda aún no publicada); si cambia la URL se vuelve a intentar.
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string>()
  const [confirmRemove, setConfirmRemove] = useState(false)
  const copy = COPY[asset]

  useEffect(() => () => (preview ? URL.revokeObjectURL(preview) : undefined), [preview])
  const shown = preview ?? (url && failedUrl !== url ? url : undefined)
  const hasImage = Boolean(url) || Boolean(preview)

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (!ACCEPTED.includes(file.type)) {
      setError('Solo se permiten imágenes PNG, JPEG o WebP.')
      return
    }
    if (file.size > copy.maxMb * 1024 * 1024) {
      setError(`El ${copy.label.toLowerCase()} no puede superar ${copy.maxMb} MB.`)
      return
    }
    setError(undefined)
    setPending(true)
    try {
      await (await getPortalService()).uploadBrand(storeId, asset, file)
      setPreview(URL.createObjectURL(file))
      invalidateAsync(`portal:${storeId}:profile`)
      toast.success(`Guardamos el ${copy.label.toLowerCase()} de tu tienda.`)
    } catch (cause) {
      setError(actionErrorMessage(cause, `No pudimos subir el ${copy.label.toLowerCase()}. Inténtalo de nuevo.`))
    } finally {
      setPending(false)
    }
  }

  async function remove() {
    await (await getPortalService()).removeBrand(storeId, asset)
    setPreview(undefined)
    invalidateAsync(`portal:${storeId}:profile`)
    toast.success(`Quitamos el ${copy.label.toLowerCase()} de tu tienda.`)
  }

  return (
    <div className="flex flex-col gap-2">
      <p id={`${inputId}-titulo`} className="text-sm font-semibold text-ap-ink">
        {copy.label}
      </p>
      <div className={cn('flex gap-4', asset === 'banner' ? 'flex-col' : 'items-center')}>
        <div
          className={cn(
            'grid shrink-0 place-items-center overflow-hidden rounded-lg border border-ap-line bg-ap-paper text-ap-muted',
            asset === 'logo' ? 'size-24' : 'aspect-[5/1] w-full',
          )}
        >
          {shown ? (
            <img
              src={shown}
              alt={`${copy.label} actual de la tienda`}
              className={cn('size-full', asset === 'logo' ? 'object-contain' : 'object-cover')}
              onError={() => (preview ? undefined : setFailedUrl(url))}
            />
          ) : (
            <span className="flex flex-col items-center gap-1 px-2 text-center text-xs">
              <ImageIcon className="size-5" aria-hidden />
              {url && !published ? 'Se verá al publicar la tienda' : 'Sin imagen'}
            </span>
          )}
        </div>
        {editable ? (
          <div className="flex flex-wrap items-center gap-2">
            <input
              ref={input}
              id={inputId}
              type="file"
              accept={ACCEPTED.join(',')}
              className="sr-only"
              aria-describedby={[error ? errorId : '', hintId].filter(Boolean).join(' ')}
              onChange={(event) => void upload(event)}
              disabled={pending}
            />
            <Button type="button" variant="outline" className="h-11 px-4" disabled={pending} onClick={() => input.current?.click()} aria-describedby={hintId}>
              {pending ? 'Subiendo…' : hasImage ? `Cambiar ${copy.label.toLowerCase()}` : `Subir ${copy.label.toLowerCase()}`}
            </Button>
            {hasImage ? (
              <Button type="button" variant="ghost" className="h-11 px-3" disabled={pending} onClick={() => setConfirmRemove(true)}>
                Quitar
              </Button>
            ) : null}
          </div>
        ) : null}
      </div>
      {error ? (
        <p id={errorId} role="alert" className="text-sm font-medium text-ap-danger">
          {error}
        </p>
      ) : null}
      <p id={hintId} className="text-xs text-ap-muted">
        {copy.hint} Se guarda al elegir el archivo.
      </p>
      <ConfirmDialog
        open={confirmRemove}
        onOpenChange={setConfirmRemove}
        title={`¿Quitar el ${copy.label.toLowerCase()}?`}
        description={asset === 'logo' ? 'En el marketplace se mostrarán las iniciales de tu tienda.' : 'Tu perfil público se verá sin banner.'}
        confirmLabel="Quitar"
        destructive
        onConfirm={remove}
      />
    </div>
  )
}
