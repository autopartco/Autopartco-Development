import { useId, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface FormSectionProps {
  /** Título de la sección; es un encabezado `h2` que nombra la región. */
  title: ReactNode
  description?: ReactNode
  /** Elemento a la derecha del título (estado, contador, ayuda). */
  aside?: ReactNode
  /** Deshabilita todos los controles de la sección (p. ej. mientras se envía). */
  disabled?: boolean
  className?: string
  children: ReactNode
  /** `id` del contenedor, útil para enfocar la sección desde un resumen de errores. */
  id?: string
}

/**
 * Sección de un formulario: agrupa campos relacionados con un título y una ayuda. Usa `fieldset`
 * para que `disabled` bloquee los controles y los lectores de pantalla anuncien el grupo; el
 * título (`h2`) es el nombre accesible.
 */
export function FormSection({ title, description, aside, disabled, className, children, id }: FormSectionProps) {
  const autoId = useId()
  const sectionId = id ?? autoId
  const titleId = `${sectionId}-titulo`
  const descriptionId = description ? `${sectionId}-descripcion` : undefined
  return (
    <fieldset
      id={sectionId}
      tabIndex={-1}
      disabled={disabled}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      className={cn('flex min-w-0 flex-col gap-4 outline-none', className)}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex min-w-0 flex-col gap-1">
          <h2 id={titleId} tabIndex={-1} className="text-xl font-extrabold tracking-tight outline-offset-4 focus-visible:rounded-sm">
            {title}
          </h2>
          {description ? (
            <p id={descriptionId} className="max-w-prose text-sm text-muted-foreground">
              {description}
            </p>
          ) : null}
        </div>
        {aside}
      </div>
      {children}
    </fieldset>
  )
}
