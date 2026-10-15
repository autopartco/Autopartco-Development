import type { ReactNode } from 'react'
import { Breadcrumbs, type Crumb } from '@/components/common/page-header'
import { cn } from '@/lib/utils'

interface PageHeaderProps {
  title: ReactNode
  description?: ReactNode
  /** Acción primaria y secundarias, alineadas a la derecha desde `sm`. */
  actions?: ReactNode
  crumbs?: Crumb[]
  /** Etiqueta mono sobre el título, como `PageIntro` del marketplace (contexto real, no decoración). */
  label?: ReactNode
  /** Contenido bajo el título (insignias, metadatos). */
  meta?: ReactNode
  className?: string
}

/**
 * Encabezado de cada pantalla del portal: migas opcionales, `h1`, descripción y acciones.
 * Misma voz tipográfica que el marketplace (800 con tracking cerrado), a escala de herramienta.
 */
export function PageHeader({ title, description, actions, crumbs, label, meta, className }: PageHeaderProps) {
  return (
    <header className={cn('mb-6 flex flex-col gap-3 sm:mb-8', className)}>
      {crumbs ? <Breadcrumbs items={crumbs} /> : null}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 flex-col gap-1.5">
          {label ? <p className="label-mono text-muted-foreground">{label}</p> : null}
          <h1 className="text-[1.75rem] leading-[1.05] font-extrabold tracking-[-0.035em] text-balance sm:text-[2.25rem]">{title}</h1>
          {description ? <div className="max-w-2xl text-muted-foreground">{description}</div> : null}
          {meta ? <div className="mt-1 flex flex-wrap items-center gap-2">{meta}</div> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  )
}
