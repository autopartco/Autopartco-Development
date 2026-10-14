import { useState, type ComponentProps } from 'react'
import { ImageOffIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

type SmartImageProps = Omit<ComponentProps<'img'>, 'width' | 'height' | 'alt'> & {
  alt: string
  width: number
  height: number
  /** Clases del contenedor (el fondo de respaldo se ve mientras carga o si falla). */
  frameClassName?: string
}

/**
 * Imagen con dimensiones explícitas (evita saltos de diseño), carga diferida
 * y un fondo de respaldo si la URL falla.
 */
export function SmartImage({ className, frameClassName, alt, loading = 'lazy', onError, ...props }: SmartImageProps) {
  const [failed, setFailed] = useState(false)
  return (
    <div className={cn('relative overflow-hidden bg-concrete', frameClassName)}>
      {failed ? (
        <div className="absolute inset-0 grid place-items-center text-muted-foreground" role="img" aria-label={alt}>
          <ImageOffIcon className="size-6" aria-hidden />
        </div>
      ) : (
        <img
          alt={alt}
          loading={loading}
          decoding="async"
          className={cn('size-full object-cover', className)}
          onError={(event) => {
            setFailed(true)
            onError?.(event)
          }}
          {...props}
        />
      )}
    </div>
  )
}
