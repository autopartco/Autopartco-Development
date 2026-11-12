import { Link } from 'react-router'
import { StarIcon } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { countPending, usePendingReviews } from '../hooks'
import { ratePath } from '../lib/paths'

/** Aviso en «Mis pedidos»: cuántas reseñas esperan. Sin pendientes (o si la consulta falla) no se muestra nada. */
export function PendingReviewsNotice() {
  const pending = usePendingReviews()
  const count = pending.data ? countPending(pending.data) : 0
  if (count === 0) return null
  return (
    <div role="status" className="mb-6 flex flex-col gap-3 rounded-xl border border-ap-line-strong bg-ap-paper p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-start gap-2.5">
        <StarIcon aria-hidden className="mt-0.5 size-5 shrink-0 text-ap-graphite" />
        <p className="text-sm text-ap-ink">
          <strong className="font-semibold">Tienes {pluralize(count, 'reseña pendiente', 'reseñas pendientes')}.</strong> Cuéntanos cómo te fue con tus últimas compras; toma menos de un minuto.
        </p>
      </div>
      <Link to={ratePath()} className={cn(buttonVariants({ variant: 'default' }), 'h-11 shrink-0 px-4')}>
        Calificar ahora
      </Link>
    </div>
  )
}
