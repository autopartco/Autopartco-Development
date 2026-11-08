import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatNumber, pluralize } from '@/lib/format'

interface ServerPagerProps {
  /** Página actual, desde 1. */
  page: number
  totalPages: number
  total: number
  singular: string
  plural: string
  onPageChange: (page: number) => void
  /** `true` mientras llega la página pedida. */
  busy?: boolean
}

/** Controles «Anterior / Siguiente» para listados que el servidor pagina. */
export function ServerPager({ page, totalPages, total, singular, plural, onPageChange, busy }: ServerPagerProps) {
  const last = Math.max(totalPages, 1)
  return (
    <nav aria-label="Paginación" className="flex flex-wrap items-center justify-between gap-3">
      <p role="status" className="text-sm text-ap-muted tabular-nums">
        Página {formatNumber(page)} de {formatNumber(last)} · {pluralize(total, singular, plural)}
      </p>
      <div className="flex gap-2">
        <Button variant="outline" className="h-11 px-3" disabled={busy || page <= 1} onClick={() => onPageChange(page - 1)}>
          <ChevronLeftIcon aria-hidden />
          Anterior
        </Button>
        <Button variant="outline" className="h-11 px-3" disabled={busy || page >= last} onClick={() => onPageChange(page + 1)}>
          Siguiente
          <ChevronRightIcon aria-hidden />
        </Button>
      </div>
    </nav>
  )
}
