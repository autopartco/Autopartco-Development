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
 * Contenedor de página (§6.4): ancho de contenido público (`max-w-content`, 1200px) y un
 * gutter compartido por cabecera, páginas y pie (16px en móvil, 24px desde sm, 32px desde lg).
 */
export function Container({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('mx-auto w-full max-w-content px-4 sm:px-6 lg:px-8', className)}>{children}</div>
}

interface PageIntroProps {
  crumbs: Crumb[]
  /**
   * Índice de sección sobre el título (`ap-index`, p. ej. `CATÁLOGO / 02`): contexto real
   * de la página, no decoración.
   */
  label?: ReactNode
  title: ReactNode
  description?: ReactNode
  children?: ReactNode
  className?: string
}

/**
 * Encabezado de las páginas internas (§5.2): migas, índice opcional, título de 28–32px y
 * entrada breve. Deja espacio a los datos y acciones que siguen.
 */
export function PageIntro({ crumbs, label, title, description, children, className }: PageIntroProps) {
  return (
    <div className={cn('mb-6 flex flex-col gap-2 sm:mb-8', className)}>
      <Breadcrumbs items={crumbs} className="mb-2 sm:mb-3" />
      {label ? <p className="ap-index">{label}</p> : null}
      <h1 className="max-w-4xl text-page-title text-ap-ink">{title}</h1>
      {description ? <div className="max-w-2xl text-base text-ap-muted">{description}</div> : null}
      {children}
    </div>
  )
}
