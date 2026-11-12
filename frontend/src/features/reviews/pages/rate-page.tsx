import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { PartyPopperIcon, StarIcon, StoreIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { SmartImage } from '@/components/common/smart-image'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ordersPath } from '@/features/orders/lib/labels'
import { cn } from '@/lib/utils'
import { ReviewDialog } from '../components/review-dialog'
import type { PendingReview } from '../api/types'
import { usePendingReviews } from '../hooks'

/** Qué se está calificando en el diálogo. */
type Active = { order: PendingReview; type: 'store' } | { order: PendingReview; type: 'product'; productId: string; name: string }

const dateFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long' })

/** El enlace del correo trae `?pedido=` con el id del pedido (también se acepta su número). */
function matchesOrder(order: PendingReview, wanted: string | null): boolean {
  if (!wanted) return false
  return order.orderId === wanted || String(order.orderNumber) === wanted.replace(/^#/, '')
}

function OrderBlock({ order, highlighted, onRate }: { order: PendingReview; highlighted: boolean; onRate: (active: Active) => void }) {
  const ref = useRef<HTMLElement>(null)
  const pendingProducts = order.products.filter((product) => !product.reviewed)
  const headingId = `pedido-${order.orderId}-${order.store.id}`

  useEffect(() => {
    if (highlighted) ref.current?.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
  }, [highlighted])

  return (
    <section
      ref={ref}
      aria-labelledby={headingId}
      className={cn('flex scroll-mt-24 flex-col gap-4 rounded-xl border bg-ap-white p-4 sm:p-5', highlighted ? 'border-ap-graphite ring-1 ring-ap-graphite' : 'border-ap-line')}
    >
      <header className="flex flex-col gap-0.5">
        <p className="ap-index">
          Pedido #{order.orderNumber} · entregado el <time dateTime={order.deliveredAt}>{dateFormatter.format(new Date(order.deliveredAt))}</time>
        </p>
        <h2 id={headingId} className="text-block-title text-ap-ink">
          {order.store.name}
        </h2>
      </header>

      {order.store.reviewed ? null : (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex min-w-0 items-start gap-2.5">
            <StoreIcon aria-hidden className="mt-0.5 size-5 shrink-0 text-ap-muted" />
            <div className="min-w-0">
              <p className="font-semibold text-ap-ink">Califica el almacén</p>
              <p className="text-sm text-ap-muted">Cuéntanos cómo fue la atención, la entrega y el pedido de {order.store.name}.</p>
            </div>
          </div>
          <Button className="h-11 shrink-0 px-4" onClick={() => onRate({ order, type: 'store' })}>
            <StarIcon aria-hidden />
            Calificar almacén
          </Button>
        </div>
      )}

      {pendingProducts.length > 0 ? (
        <div className={cn('flex flex-col gap-1', !order.store.reviewed && 'border-t border-ap-line pt-4')}>
          <h3 className="text-sm font-semibold text-ap-ink">Califica cada producto</h3>
          <ul className="flex flex-col divide-y divide-ap-line">
            {pendingProducts.map((product) => (
              <li key={product.productId} className="flex items-center gap-3 py-3">
                <SmartImage src={product.image ?? ''} alt="" width={56} height={56} frameClassName="size-14 shrink-0 rounded-md border border-ap-line" />
                <p className="min-w-0 flex-1 text-sm font-medium break-words text-ap-ink">{product.name}</p>
                <Button
                  variant="outline"
                  className="h-11 shrink-0 px-3"
                  aria-label={`Calificar ${product.name}`}
                  onClick={() => onRate({ order, type: 'product', productId: product.productId, name: product.name })}
                >
                  Calificar
                </Button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  )
}

/**
 * `/cuenta/calificar`: lo que el comprador aún no calificó de sus pedidos entregados (`GET /account/reviews/pending`).
 * Con `?pedido=` (enlace del correo) sube y resalta ese pedido. Al calificar, el elemento sale de la lista
 * porque el servidor lo marca como calificado.
 */
export default function RatePage() {
  const [params] = useSearchParams()
  const wanted = params.get('pedido')
  const pending = usePendingReviews()
  const [active, setActive] = useState<Active>()
  const [thanks, setThanks] = useState<string>()
  const thanksRef = useRef<HTMLParagraphElement>(null)

  const orders = (pending.data ?? [])
    .filter((order) => !order.store.reviewed || order.products.some((product) => !product.reviewed))
    .toSorted((a, b) => Number(matchesOrder(b, wanted)) - Number(matchesOrder(a, wanted)))

  function done(outcome: 'created' | 'updated' | 'discarded') {
    if (outcome !== 'created') return
    setThanks(active?.type === 'product' ? `¡Gracias por calificar ${active.name}! Tu opinión ayuda a otros compradores.` : `¡Gracias por calificar a ${active?.order.store.name}! Tu opinión ayuda a otros compradores.`)
    // El botón que abrió el diálogo desaparece de la lista: el foco pasa al mensaje de gracias.
    window.setTimeout(() => thanksRef.current?.focus(), 50)
  }

  return (
    <Container className="py-6 sm:py-8">
      <title>Califica tus compras | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Mis pedidos', to: ordersPath() }, { label: 'Calificar' }]}
        label="Tu cuenta"
        title="Califica tus compras"
        description="Cuéntanos cómo te fue con el almacén y con cada repuesto que recibiste. Toma menos de un minuto."
      />

      <p
        ref={thanksRef}
        tabIndex={-1}
        role="status"
        className={cn('outline-none', thanks ? 'mb-5 flex items-start gap-2.5 rounded-xl border border-ap-success/30 bg-ap-success-soft p-4 text-sm font-medium text-ap-ink' : 'sr-only')}
      >
        {thanks ? <PartyPopperIcon aria-hidden className="mt-0.5 size-5 shrink-0 text-ap-success" /> : null}
        {thanks}
      </p>

      {pending.status === 'error' ? (
        <ErrorState title="No pudimos cargar lo que tienes por calificar" onRetry={pending.retry} />
      ) : !pending.data ? (
        <div className="flex flex-col gap-4" aria-hidden>
          <span className="sr-only" role="status">
            Cargando tus pedidos por calificar
          </span>
          <Skeleton className="h-40 w-full rounded-xl" />
          <Skeleton className="h-40 w-full rounded-xl" />
        </div>
      ) : orders.length === 0 ? (
        <EmptyState
          icon={<StarIcon />}
          title={thanks ? 'Listo, ya calificaste todo' : 'No tienes nada por calificar'}
          description={
            thanks
              ? 'Gracias por ayudar a otros compradores a elegir mejor.'
              : 'Cuando recibas un pedido, aquí podrás calificar al almacén y cada repuesto. Revisa el estado de tus compras en Mis pedidos.'
          }
        >
          <Button nativeButton={false} render={<Link to={ordersPath()} />}>
            Ver mis pedidos
          </Button>
        </EmptyState>
      ) : (
        <div className={cn('flex flex-col gap-5', pending.isStale && 'opacity-70')} aria-busy={pending.status === 'loading' || undefined}>
          {orders.map((order) => (
            <OrderBlock key={`${order.orderId}:${order.store.id}`} order={order} highlighted={matchesOrder(order, wanted)} onRate={setActive} />
          ))}
          <p className="text-sm text-ap-muted">
            ¿Quieres ver el detalle de una compra?{' '}
            <Link to={ordersPath()} className="font-semibold text-ap-red underline-offset-4 hover:underline">
              Ir a Mis pedidos
            </Link>
          </p>
        </div>
      )}

      {active ? (
        <ReviewDialog
          open
          onOpenChange={(open) => !open && setActive(undefined)}
          target={active.type === 'store' ? { type: 'store', id: active.order.store.id } : { type: 'product', id: active.productId }}
          targetName={active.type === 'store' ? active.order.store.name : active.name}
          orders={[{ id: active.order.orderId, label: `Pedido #${active.order.orderNumber}` }]}
          onDone={done}
        />
      ) : null}
    </Container>
  )
}

