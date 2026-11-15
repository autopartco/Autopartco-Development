import { Link } from 'react-router'
import { ChevronRightIcon } from 'lucide-react'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ServiceCategoryCount } from '../api/types'
import { serviceCategoryIcon } from '../lib/categories'
import { SERVICES_PATH, servicesCategoryPath } from '../lib/paths'

interface ServiceCategoriesProps {
  categories: ServiceCategoryCount[] | undefined
  /** `true` si falló la carga; se ofrece reintentar. */
  failed?: boolean
  onRetry?: () => void
  /** Categoría elegida: se marca con la línea de acento y `aria-current`. */
  selected?: string
  /** Etiqueta accesible de la lista. */
  label?: string
  className?: string
}

/**
 * Categorías de servicio como índice de filas con ícono, nombre y cantidad (no tarjetas). Cada una enlaza a la URL
 * canónica `/servicios?category=…`.
 */
export function ServiceCategories({ categories, failed, onRetry, selected, label = 'Categorías de servicio', className }: ServiceCategoriesProps) {
  if (failed) return <ErrorState title="No pudimos cargar las categorías de servicio" onRetry={onRetry} className={className} />
  if (!categories) {
    return (
      <ul className={cn('grid gap-x-8 sm:grid-cols-2 lg:grid-cols-4', className)} aria-hidden>
        {Array.from({ length: 8 }, (_, index) => (
          <li key={index} className="border-t border-ap-line py-4">
            <Skeleton className="h-5 w-2/3" />
          </li>
        ))}
      </ul>
    )
  }
  if (categories.length === 0) {
    return (
      <EmptyState
        title="Aún no hay servicios publicados"
        description="Cuando los talleres publiquen sus servicios, verás aquí las categorías. Mientras tanto, busca lo que necesitas en el buscador."
        className={className}
      >
        <Link to="/buscar" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
          Buscar repuestos
        </Link>
      </EmptyState>
    )
  }
  return (
    <ul aria-label={label} className={cn('grid gap-x-8 border-b border-ap-line sm:grid-cols-2 lg:grid-cols-4', className)}>
      {categories.map((item) => {
        const Icon = serviceCategoryIcon(item.category, item.icon)
        const active = item.category === selected
        return (
          <li key={item.category} className="border-t border-ap-line">
            <Link
              // Con la categoría elegida, el mismo enlace la quita (vuelve a todos los servicios).
              to={active ? SERVICES_PATH : servicesCategoryPath(item.category)}
              aria-current={active ? 'true' : undefined}
              className={cn(
                'group flex min-h-14 items-center gap-3 py-3 pl-3 transition-colors hover:text-ap-red',
                active && 'bg-ap-paper shadow-[inset_3px_0_0_var(--ap-red)]',
              )}
            >
              <Icon className={cn('size-5 shrink-0 group-hover:text-ap-red', active ? 'text-ap-red' : 'text-ap-steel')} aria-hidden />
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-ap-ink group-hover:text-ap-red">{item.label}</span>
                <span className="text-sm text-ap-muted tabular-nums">{pluralize(item.count, 'servicio', 'servicios')}</span>
              </span>
              <ChevronRightIcon className="size-4 shrink-0 text-ap-steel group-hover:text-ap-red" aria-hidden />
              {active ? <span className="sr-only">(seleccionada; actívala para quitarla)</span> : null}
            </Link>
          </li>
        )
      })}
    </ul>
  )
}
