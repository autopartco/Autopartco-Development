import { useState } from 'react'
import { Link } from 'react-router'
import { ChevronLeftIcon, ChevronRightIcon, PackageSearchIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useAsync } from '@/lib/use-async'
import { getOrdersService } from '../api/orders-service'
import { OrderCard } from '../components/order-card'

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
        <div role="status" aria-busy className="flex flex-col gap-4">
          <span className="sr-only">Cargando tus pedidos</span>
          {[0, 1, 2].map((key) => (
            <Skeleton key={key} className="h-40 w-full rounded-2xl" />
          ))}
        </div>
      ) : data.items.length === 0 && data.total === 0 ? (
        <EmptyState icon={<PackageSearchIcon />} title="Todavía no tienes pedidos" description="Cuando compres un repuesto, tu pedido aparecerá aquí con su seguimiento.">
          <Button className="h-11 px-5" nativeButton={false} render={<Link to="/buscar" />}>
            Buscar repuestos
          </Button>
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-4" aria-busy={orders.isStale || undefined}>
          <ul className={`flex flex-col gap-4 transition-opacity ${orders.isStale ? 'opacity-60' : ''}`}>
            {data.items.map((order) => (
              <li key={order.orderId}>
                <OrderCard order={order} />
              </li>
            ))}
          </ul>
          {data.totalPages > 1 ? (
            <nav aria-label="Paginación de pedidos" className="flex items-center justify-between gap-3">
              <Button variant="outline" className="h-11 px-4" disabled={page <= 1} onClick={() => setPage(page - 1)}>
                <ChevronLeftIcon aria-hidden />
                Anteriores
              </Button>
              <p className="text-sm text-muted-foreground" aria-live="polite">
                Página {data.page} de {data.totalPages}
              </p>
              <Button variant="outline" className="h-11 px-4" disabled={page >= data.totalPages} onClick={() => setPage(page + 1)}>
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
