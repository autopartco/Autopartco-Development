import { Link } from 'react-router'
import { PackageIcon, PlusIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatCOP, formatNumber } from '@/lib/format'
import { getDashboardService } from '../api/dashboard-service'
import type { Dashboard } from '../api/types'
import { CardsSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatCard } from '../components/stat-card'
import { StatusBadge } from '../components/status-badge'
import { UsageMeter } from '../components/usage-meter'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { formatDate, formatDays, SUBSCRIPTION_STATUS_LABEL, SUBSCRIPTION_STATUS_TONE } from '../lib/labels'

function DashboardView({ data }: { data: Dashboard }) {
  const { storeId, can } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const status = data.subscriptionStatus
  const days = data.daysRemaining
  const emptyCatalog = data.usage.products === 0

  return (
    <div className="flex flex-col gap-6">
      {data.setupRequired ? (
        <p role="status" className="rounded-lg border border-stock-low/30 bg-stock-low/10 p-4 text-sm">
          <strong className="font-semibold">Falta terminar la configuración del almacén.</strong> Abre el enlace que te enviamos al correo para completarla.
        </p>
      ) : null}

      {emptyCatalog ? (
        <EmptyState
          icon={<PackageIcon />}
          title="Carga tu primer repuesto"
          description="Los compradores solo ven lo que publiques. Empieza con un producto: nombre, precio, existencias y los vehículos en los que sirve."
        >
          {can('PRODUCT_CREATE') ? (
            <Button className="h-11 px-5" nativeButton={false} render={<Link to={`${base}/productos/nuevo`} />}>
              <PlusIcon aria-hidden />
              Agregar producto
            </Button>
          ) : null}
        </EmptyState>
      ) : null}

      <section aria-labelledby="ventas-titulo" className="flex flex-col gap-3">
        <h2 id="ventas-titulo" className="text-lg font-extrabold tracking-tight">
          Ventas de este mes
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Ingresos" value={formatCOP(data.sales.revenueThisMonth)} hint="Pedidos no cancelados" />
          <StatCard label="Pedidos" value={formatNumber(data.sales.ordersThisMonth)} hint="Recibidos este mes" to={can('ORDER_READ') ? `${base}/pedidos` : undefined} />
          <StatCard
            label="Por atender"
            value={formatNumber(data.sales.pendingOrders)}
            hint={data.sales.pendingOrders > 0 ? 'Confirma o cancela cada pedido' : 'No hay pedidos nuevos'}
            tone={data.sales.pendingOrders > 0 ? 'attention' : 'default'}
            to={can('ORDER_READ') ? `${base}/pedidos?estado=PENDING` : undefined}
          />
          <StatCard
            label="En preparación"
            value={formatNumber(data.sales.preparingOrders)}
            hint="Listos para registrar el envío"
            to={can('ORDER_READ') ? `${base}/pedidos?estado=PREPARING` : undefined}
          />
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        {data.inventory ? (
          <section aria-labelledby="inventario-titulo" className="flex flex-col gap-3">
            <h2 id="inventario-titulo" className="text-lg font-extrabold tracking-tight">
              Inventario
            </h2>
            <div className="grid gap-4 sm:grid-cols-3 lg:grid-cols-1 xl:grid-cols-3">
              <StatCard label="Publicaciones" value={formatNumber(data.inventory.listings)} hint="Producto por sucursal" to={can('PRODUCT_READ') ? `${base}/productos` : undefined} />
              <StatCard
                label="Agotados"
                value={formatNumber(data.inventory.outOfStock)}
                hint={data.inventory.outOfStock > 0 ? 'Reponer para vender' : 'Sin agotados'}
                tone={data.inventory.outOfStock > 0 ? 'attention' : 'default'}
                to={can('PRODUCT_READ') ? `${base}/productos?stock=agotado` : undefined}
              />
              <StatCard
                label="Stock bajo"
                value={formatNumber(data.inventory.lowStock)}
                hint="De 1 a 3 unidades"
                tone={data.inventory.lowStock > 0 ? 'attention' : 'default'}
                to={can('PRODUCT_READ') ? `${base}/productos?stock=bajo` : undefined}
              />
            </div>
          </section>
        ) : null}

        <Card className="gap-4 p-5">
          <CardHeader className="gap-1.5 px-0">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle className="text-lg font-extrabold tracking-tight">Tu plan: {data.plan.name}</CardTitle>
              {status ? <StatusBadge tone={SUBSCRIPTION_STATUS_TONE[status]}>{SUBSCRIPTION_STATUS_LABEL[status]}</StatusBadge> : null}
            </div>
            <CardDescription>
              {status === 'EXPIRED'
                ? 'Tu suscripción venció.'
                : data.paidThrough || data.trialEndsAt
                  ? `Vigente hasta ${formatDate(status === 'ACTIVE' ? data.paidThrough : data.trialEndsAt)}${days !== null && days !== undefined ? ` (${formatDays(days)})` : ''}.`
                  : 'Sin fecha de vigencia.'}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4 px-0">
            <UsageMeter capacity="PRODUCTS" used={data.usage.products} limit={data.maxProducts} upgradeTo={`${base}/plan`} />
            <UsageMeter capacity="BRANCHES" used={data.usage.branches} limit={data.maxBranches} upgradeTo={`${base}/plan`} />
            <UsageMeter capacity="USERS" used={data.usage.users} limit={data.maxUsers} upgradeTo={`${base}/plan`} />
            <Button variant="outline" className="h-11 w-fit px-4" nativeButton={false} render={<Link to={`${base}/plan`} />}>
              Ver plan y suscripción
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

/** `/portal/:storeId/inicio`: ventas del mes, inventario por atender y uso del plan. */
export default function PortalOverviewPage() {
  const { store, can, storeId } = usePortal()
  const dashboard = useStoreQuery('dashboard', async (id) => (await getDashboardService()).dashboard(id))
  const name = dashboard.data?.storeName ?? store?.businessName

  return (
    <>
      <title>{`Inicio | ${name ?? 'Portal'} | Autopartco`}</title>
      <PageHeader
        title="Inicio"
        description={name ? `Resumen de ${name}.` : 'Resumen de tu almacén.'}
        actions={
          can('PRODUCT_CREATE') ? (
            <Button className="h-11 px-4" nativeButton={false} render={<Link to={`/portal/${encodeURIComponent(storeId)}/productos/nuevo`} />}>
              <PlusIcon aria-hidden />
              Agregar producto
            </Button>
          ) : null
        }
      />
      <QueryBoundary result={dashboard} what="el resumen del almacén" skeleton={<CardsSkeleton label="Cargando el resumen" />}>
        {(data) => <DashboardView data={data} />}
      </QueryBoundary>
    </>
  )
}
