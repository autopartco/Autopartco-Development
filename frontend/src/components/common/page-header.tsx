import { Fragment, type ReactNode } from 'react'
import { Link } from 'react-router'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { cn } from '@/lib/utils'

export interface Crumb {
  label: string
  to?: string
}

export function Breadcrumbs({ items, className }: { items: Crumb[]; className?: string }) {
  return (
    <Breadcrumb aria-label="Ruta de navegación" className={className}>
      <BreadcrumbList>
        {items.map((item, index) => (
          <Fragment key={`${item.label}-${index}`}>
            {index > 0 ? <BreadcrumbSeparator /> : null}
            <BreadcrumbItem className="max-w-[16rem] truncate">
              {item.to ? (
                <BreadcrumbLink render={<Link to={item.to} />}>{item.label}</BreadcrumbLink>
              ) : (
                <BreadcrumbPage className="truncate">{item.label}</BreadcrumbPage>
              )}
            </BreadcrumbItem>
          </Fragment>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  )
}

/**
 * Contenedor de página: un único ancho máximo (80rem) y un gutter compartido
 * por cabecera, páginas y pie (16px en móvil, 24px desde sm, 32px desde lg).
 */
export function Container({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8', className)}>{children}</div>
}

interface PageIntroProps {
  crumbs: Crumb[]
  /** Etiqueta mono sobre el título: contexto real de la página (no decoración). */
  label?: ReactNode
  title: ReactNode
  description?: ReactNode
  children?: ReactNode
  className?: string
}

/** Encabezado de las páginas internas: migas, etiqueta mono, título display y entrada. */
export function PageIntro({ crumbs, label, title, description, children, className }: PageIntroProps) {
  return (
    <div className={cn('mb-8 flex flex-col gap-3 sm:mb-10', className)}>
      <Breadcrumbs items={crumbs} className="mb-3 sm:mb-5" />
      {label ? <p className="label-mono text-muted-foreground">{label}</p> : null}
      <h1 className="max-w-4xl text-page-title">{title}</h1>
      {description ? <div className="max-w-2xl text-base text-muted-foreground sm:text-lg">{description}</div> : null}
      {children}
    </div>
  )
}
