import { useState, type ComponentProps } from 'react'
import { ImageOffIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

type SmartImageProps = Omit<ComponentProps<'img'>, 'width' | 'height' | 'alt'> & {
  alt: string
  width: number
  height: number
  /** Clases del contenedor (el fondo papel se ve mientras carga o si falla). */
  frameClassName?: string
}

/**
 * Imagen de producto (§9): dimensiones explícitas (evita saltos de diseño), carga diferida,
 * `object-fit: contain` para no recortar conectores ni rasgos de la pieza y fondo papel
 * consistente. Si no hay URL (p. ej. la lista de imágenes del producto llega vacía) o la URL
 * falla, muestra un placeholder sobrio "Imagen no disponible".
 * Para fotos de ambiente o portadas, el llamador puede pasar `object-cover` en `className`.
 */
export function SmartImage({ className, frameClassName, alt, loading = 'lazy', onError, src, ...props }: SmartImageProps) {
  // El fallo se recuerda por URL: si cambia `src`, se vuelve a intentar sin efectos.
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const url = typeof src === 'string' ? src.trim() : ''
  const unavailable = url === '' || failedSrc === url
  return (
    <div className={cn('relative overflow-hidden bg-ap-paper', frameClassName)}>
      {unavailable ? (
        <div
          className="absolute inset-0 flex flex-col items-center justify-center gap-1.5 p-2 text-center text-ap-muted"
          role="img"
          aria-label={alt ? `${alt}: imagen no disponible` : 'Imagen no disponible'}
        >
          <ImageOffIcon className="size-5 shrink-0" aria-hidden />
          <span className="text-xs leading-tight" aria-hidden>
            Imagen no disponible
          </span>
        </div>
      ) : (
        <img
          alt={alt}
          loading={loading}
          decoding="async"
          className={cn('size-full object-contain', className)}
          onError={(event) => {
            setFailedSrc(url)
            onError?.(event)
          }}
          src={url}
          {...props}
        />
      )}
    </div>
  )
}
