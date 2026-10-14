import { Link, useLocation, useSearchParams } from 'react-router'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Pagination, PaginationContent, PaginationEllipsis, PaginationItem } from '@/components/ui/pagination'
import { cn } from '@/lib/utils'
import { QUERY_PARAMS } from '../lib/product-query'

/** Páginas visibles: primera, última y vecinas de la actual, con huecos marcados como `null`. */
function pageWindow(current: number, total: number): (number | null)[] {
  const pages = new Set([1, total, current - 1, current, current + 1])
  const sorted = [...pages].filter((page) => page >= 1 && page <= total).toSorted((a, b) => a - b)
  const result: (number | null)[] = []
  sorted.forEach((page, index) => {
    const previous = sorted[index - 1]
    if (previous !== undefined && page - previous > 1) result.push(null)
    result.push(page)
  })
  return result
}

/** Paginación con enlaces reales (`?pagina=`), navegable con teclado y compartible. */
export function ResultsPagination({ page, totalPages }: { page: number; totalPages: number }) {
  const { pathname } = useLocation()
  const [searchParams] = useSearchParams()
  if (totalPages <= 1) return null

  const hrefFor = (target: number) => {
    const params = new URLSearchParams(searchParams)
    if (target > 1) params.set(QUERY_PARAMS.page, String(target))
    else params.delete(QUERY_PARAMS.page)
    const qs = params.toString()
    return qs ? `${pathname}?${qs}` : pathname
  }

  const edge = (target: number, label: string, disabled: boolean, icon: 'prev' | 'next') => (
    <Button
      variant="ghost"
      className="h-11 gap-1 px-3"
      nativeButton={false}
      aria-disabled={disabled || undefined}
      tabIndex={disabled ? -1 : undefined}
      render={
        disabled ? (
          <span className="pointer-events-none opacity-40" />
        ) : (
          <Link to={hrefFor(target)} aria-label={`${label}: página ${target}`} />
        )
      }
    >
      {icon === 'prev' ? <ChevronLeftIcon aria-hidden /> : null}
      <span className="hidden sm:inline">{label}</span>
      {icon === 'next' ? <ChevronRightIcon aria-hidden /> : null}
    </Button>
  )

  return (
    <Pagination aria-label="Páginas de resultados" className="pt-4">
      <PaginationContent className="flex-wrap gap-1">
        <PaginationItem>{edge(page - 1, 'Anterior', page <= 1, 'prev')}</PaginationItem>
        {pageWindow(page, totalPages).map((target, index) =>
          target === null ? (
            <PaginationItem key={`gap-${index}`}>
              <PaginationEllipsis />
            </PaginationItem>
          ) : (
            <PaginationItem key={target}>
              <Button
                variant={target === page ? 'outline' : 'ghost'}
                className={cn('size-11 tabular-nums', target === page && 'border-foreground font-semibold')}
                nativeButton={false}
                render={
                  <Link
                    to={hrefFor(target)}
                    aria-label={`Página ${target}`}
                    aria-current={target === page ? 'page' : undefined}
                  />
                }
              >
                {target}
              </Button>
            </PaginationItem>
          ),
        )}
        <PaginationItem>{edge(page + 1, 'Siguiente', page >= totalPages, 'next')}</PaginationItem>
      </PaginationContent>
    </Pagination>
  )
}
