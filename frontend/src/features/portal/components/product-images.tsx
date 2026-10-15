import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { ImagePlusIcon, StarIcon, Trash2Icon } from 'lucide-react'
import { SmartImage } from '@/components/common/smart-image'
import { Button } from '@/components/ui/button'
import { getProductService } from '../api/product-service'
import type { ProductImage } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { ConfirmDialog } from './confirm-dialog'

const MAX_IMAGES = 10
const MAX_BYTES = 5 * 1024 * 1024
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp']

interface ProductImagesProps {
  productId: string
  productName: string
  images: ProductImage[]
}

/**
 * Galería del producto: subir (JPG, PNG o WebP de hasta 5 MB, máximo 10), marcar la principal y
 * borrar. El servidor valida el contenido real del archivo; aquí solo se avisa a tiempo.
 */
export function ProductImages({ productId, productName, images }: ProductImagesProps) {
  const { storeId, can, invalidateStore, promptUpgrade } = usePortal()
  const editable = can('PRODUCT_IMAGE_MANAGE')
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [busyImage, setBusyImage] = useState<string>()
  const [problem, setProblem] = useState<string>()
  const [toDelete, setToDelete] = useState<ProductImage>()

  const sorted = images.toSorted((a, b) => Number(b.isMain) - Number(a.isMain) || a.displayOrder - b.displayOrder)

  async function handleFiles(list: FileList | null) {
    const files = list ? [...list] : []
    if (files.length === 0) return
    setProblem(undefined)
    if (images.length + files.length > MAX_IMAGES) {
      setProblem(`Un producto admite hasta ${MAX_IMAGES} imágenes y ya tiene ${images.length}.`)
      return
    }
    const invalid = files.find((file) => !ACCEPTED.includes(file.type))
    if (invalid) {
      setProblem(`“${invalid.name}” no es una imagen JPG, PNG o WebP.`)
      return
    }
    const tooBig = files.find((file) => file.size > MAX_BYTES)
    if (tooBig) {
      setProblem(`“${tooBig.name}” pesa más de 5 MB. Redúcela e inténtalo de nuevo.`)
      return
    }
    setUploading(true)
    try {
      await (await getProductService()).uploadImages(storeId, productId, files)
      invalidateStore()
      toast.success(files.length === 1 ? 'Imagen subida.' : `${files.length} imágenes subidas.`)
    } catch (error) {
      setProblem(actionErrorMessage(error, 'No pudimos subir las imágenes. Inténtalo de nuevo.'))
    } finally {
      setUploading(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  async function makeMain(image: ProductImage) {
    setBusyImage(image.id)
    setProblem(undefined)
    try {
      await (await getProductService()).setMainImage(storeId, productId, image.id)
      invalidateStore()
      toast.success('Cambiaste la imagen principal.')
    } catch (error) {
      if (!promptUpgrade(error)) setProblem(actionErrorMessage(error, 'No pudimos cambiar la imagen principal.'))
    } finally {
      setBusyImage(undefined)
    }
  }

  return (
    <section aria-labelledby="imagenes-titulo" className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 id="imagenes-titulo" className="text-xl font-extrabold tracking-tight">
          Imágenes
        </h2>
        <p className="text-sm text-muted-foreground">
          JPG, PNG o WebP de hasta 5 MB. Máximo {MAX_IMAGES} por producto. La principal es la que ven primero los compradores.
        </p>
      </div>

      {problem ? (
        <p role="alert" className="rounded-lg border border-destructive/25 bg-destructive/5 p-3 text-sm font-medium text-destructive">
          {problem}
        </p>
      ) : null}

      {sorted.length === 0 ? (
        <p className="rounded-lg bg-concrete p-4 text-sm text-muted-foreground">Este producto todavía no tiene imágenes. Una buena foto ayuda a vender más.</p>
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {sorted.map((image, index) => (
            <li key={image.id} className="flex flex-col gap-2 rounded-lg bg-card p-2 ring-1 ring-foreground/10">
              <SmartImage
                src={image.url}
                alt={`${productName}, imagen ${index + 1}${image.isMain ? ' (principal)' : ''}`}
                width={240}
                height={240}
                frameClassName="aspect-square w-full rounded-md"
              />
              {image.isMain ? (
                <span className="inline-flex h-7 items-center gap-1.5 text-xs font-semibold text-recommended">
                  <StarIcon className="size-3.5 fill-current" aria-hidden />
                  Principal
                </span>
              ) : null}
              {editable ? (
                <div className="flex flex-wrap gap-1">
                  {image.isMain ? null : (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-9 flex-1 pointer-coarse:h-11"
                      disabled={busyImage === image.id}
                      onClick={() => void makeMain(image)}
                      aria-label={`Hacer principal la imagen ${index + 1}`}
                    >
                      <StarIcon aria-hidden />
                      Principal
                    </Button>
                  )}
                  <Button
                    variant="destructive"
                    size="sm"
                    className="h-9 flex-1 pointer-coarse:h-11"
                    onClick={() => setToDelete(image)}
                    aria-label={`Eliminar la imagen ${index + 1}`}
                  >
                    <Trash2Icon aria-hidden />
                    Quitar
                  </Button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      {editable ? (
        <div>
          <input
            ref={inputRef}
            id="producto-imagenes"
            type="file"
            accept={ACCEPTED.join(',')}
            multiple
            className="sr-only"
            tabIndex={-1}
            aria-hidden
            disabled={uploading || images.length >= MAX_IMAGES}
            onChange={(event) => void handleFiles(event.target.files)}
          />
          <Button
            type="button"
            variant="outline"
            className="h-11 px-4"
            disabled={uploading || images.length >= MAX_IMAGES}
            onClick={() => inputRef.current?.click()}
          >
            <ImagePlusIcon aria-hidden />
            {uploading ? 'Subiendo…' : 'Agregar imágenes'}
          </Button>
          {images.length >= MAX_IMAGES ? <p className="mt-2 text-sm text-muted-foreground">Llegaste al máximo de {MAX_IMAGES} imágenes.</p> : null}
        </div>
      ) : null}

      <ConfirmDialog
        open={toDelete !== undefined}
        onOpenChange={(open) => !open && setToDelete(undefined)}
        title="¿Quitar esta imagen?"
        description={toDelete?.isMain ? 'Es la imagen principal: la siguiente pasará a ser la principal.' : 'Se elimina del producto y del almacenamiento.'}
        confirmLabel="Quitar imagen"
        destructive
        onConfirm={async () => {
          if (!toDelete) return
          await (await getProductService()).removeImage(storeId, productId, toDelete.id)
          invalidateStore()
          toast.success('Imagen eliminada.')
        }}
      />
    </section>
  )
}
