import type { ReactNode } from 'react'
import { Breadcrumbs, type Crumb } from '@/components/common/page-header'
import { cn } from '@/lib/utils'

interface PageHeaderProps {
  title: ReactNode
  description?: ReactNode
  /** Acción principal (una por región) y secundarias, alineadas a la derecha desde `sm`. */
  actions?: ReactNode
  crumbs?: Crumb[]
  /** Índice de sección sobre el título (`ap-index`): contexto real, no decoración. */
  label?: ReactNode
  /** Contenido bajo el título (insignias, metadatos). */
  meta?: ReactNode
  className?: string
}

/**
 * Encabezado de cada pantalla del portal (§5.2, §7.3): migas opcionales, `h1` de herramienta
 * (28–32px, 600), descripción breve y la acción principal a la derecha. Deja espacio a los datos.
 */
export function PageHeader({ title, description, actions, crumbs, label, meta, className }: PageHeaderProps) {
  return (
    <header className={cn('mb-6 flex flex-col gap-3', className)}>
      {crumbs ? <Breadcrumbs items={crumbs} /> : null}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex min-w-0 flex-col gap-1.5">
          {label ? <p className="ap-index">{label}</p> : null}
          <h1 className="text-page-title text-ap-ink">{title}</h1>
          {description ? <div className="max-w-2xl text-ap-muted">{description}</div> : null}
          {meta ? <div className="mt-1 flex flex-wrap items-center gap-2">{meta}</div> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  )
}
