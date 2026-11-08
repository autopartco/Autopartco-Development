import { useState } from 'react'
import { Link } from 'react-router'
import { ChevronLeftIcon, ChevronRightIcon, PackageSearchIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useAsync } from '@/lib/use-async'
import { getOrdersService } from '../api/orders-service'
import { ORDER_ROW_GRID, OrderCard } from '../components/order-card'

const PAGE_SIZE = 10

export default function OrdersPage() {
  const [page, setPage] = useState(1)
  // Prefijo `portal:` para que se descarte al cerrar sesión (ver RequireSession).
  const orders = useAsync(`portal:buyer-orders:${page}`, () => getOrdersService().then((service) => service.list(page, PAGE_SIZE)))
  const data = orders.data

  return (
    <Container className="py-6 sm:py-8">
      <title>Mis pedidos | Autopartco</title>
      <PageIntro crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Mis pedidos' }]} label="Tu cuenta" title="Mis pedidos" description="Revisa tus compras y sigue el envío de cada una." />

      {orders.status === 'error' ? (
        <ErrorState title="No pudimos cargar tus pedidos" onRetry={orders.retry} />
      ) : !data ? (
        <div className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
          <span className="sr-only" role="status">
            Cargando tus pedidos
          </span>
          {[0, 1, 2, 3].map((key) => (
            <div key={key} className="flex flex-col gap-2 py-4 sm:flex-row sm:items-center sm:gap-5" aria-hidden>
              <Skeleton className="h-10 w-36" />
              <Skeleton className="h-5 flex-1" />
              <Skeleton className="h-6 w-28" />
            </div>
          ))}
        </div>
      ) : data.items.length === 0 && data.total === 0 ? (
        <EmptyState icon={<PackageSearchIcon />} title="Todavía no tienes pedidos" description="Cuando compres un repuesto, tu pedido aparecerá aquí con su seguimiento.">
          <Button nativeButton={false} render={<Link to="/buscar" />}>
            Buscar repuestos
          </Button>
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-4" aria-busy={orders.isStale || undefined}>
          <div className={`transition-opacity duration-150 ${orders.isStale ? 'opacity-60' : ''}`}>
            <div aria-hidden className={`hidden border-b border-ap-graphite px-3 pb-2 sm:grid ${ORDER_ROW_GRID}`}>
              <span className="ap-index">Pedido</span>
              <span className="ap-index">Tiendas</span>
              <span className="ap-index">Estado</span>
              <span className="ap-index text-right">Total</span>
              <span />
            </div>
            <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line sm:border-t-0">
              {data.items.map((order) => (
                <li key={order.orderId}>
                  <OrderCard order={order} />
                </li>
              ))}
            </ul>
          </div>
          {data.totalPages > 1 ? (
            <nav aria-label="Paginación de pedidos" className="flex items-center justify-between gap-3">
              <Button variant="outline" disabled={page <= 1} onClick={() => setPage(page - 1)}>
                <ChevronLeftIcon aria-hidden />
                Anteriores
              </Button>
              <p className="font-mono text-sm text-ap-muted tabular-nums" aria-live="polite">
                Página {data.page} de {data.totalPages}
              </p>
              <Button variant="outline" disabled={page >= data.totalPages} onClick={() => setPage(page + 1)}>
                Siguientes
                <ChevronRightIcon aria-hidden />
              </Button>
            </nav>
          ) : null}
        </div>
      )}
    </Container>
  )
}
