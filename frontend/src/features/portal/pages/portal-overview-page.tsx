import type { ComponentType } from 'react'
import { Link } from 'react-router'
import { AlertTriangleIcon, ClipboardListIcon, CreditCardIcon, PackageIcon, PlusIcon, TruckIcon, type LucideProps } from 'lucide-react'
import { getSalesService } from '../api/sales-service'
import { AnimatedNumber, BentoTile, RevealGroup } from '../components/bento'
import { OrderFunnel } from '../components/order-funnel'
import { Sparkline } from '../components/sparkline'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatCOP, formatNumber, pluralize } from '@/lib/format'
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

interface PendingItem {
  key: string
  icon: ComponentType<LucideProps>
  text: string
  to: string
  cta: string
}

/** Lo que pide acción hoy, de más a menos urgente. Solo muestra lo que el servidor reporta. */
function PendingActions({ data }: { data: Dashboard }) {
  const { storeId, can } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const items: PendingItem[] = []
  if (data.subscriptionStatus === 'EXPIRED') {
    items.push({ key: 'plan', icon: CreditCardIcon, text: 'Tu suscripción venció: renuévala para seguir vendiendo.', to: `${base}/plan`, cta: 'Renovar' })
  } else if (data.daysRemaining !== null && data.daysRemaining !== undefined && data.daysRemaining <= 7) {
    items.push({ key: 'plan', icon: CreditCardIcon, text: `Tu plan vence en ${formatDays(data.daysRemaining)}.`, to: `${base}/plan`, cta: 'Ver plan' })
  }
  if (data.sales.pendingOrders > 0 && can('ORDER_READ')) {
    items.push({ key: 'pending', icon: ClipboardListIcon, text: `${formatNumber(data.sales.pendingOrders)} ${pluralize(data.sales.pendingOrders, 'pedido espera', 'pedidos esperan')} tu confirmación.`, to: `${base}/pedidos?estado=PENDING`, cta: 'Atender' })
  }
  if (data.sales.preparingOrders > 0 && can('ORDER_READ')) {
    items.push({ key: 'preparing', icon: TruckIcon, text: `${formatNumber(data.sales.preparingOrders)} ${pluralize(data.sales.preparingOrders, 'pedido listo', 'pedidos listos')} para registrar el envío.`, to: `${base}/pedidos?estado=PREPARING`, cta: 'Registrar envío' })
  }
  if (data.inventory && data.inventory.outOfStock > 0 && can('PRODUCT_READ')) {
    items.push({ key: 'out', icon: PackageIcon, text: `${formatNumber(data.inventory.outOfStock)} ${pluralize(data.inventory.outOfStock, 'publicación agotada', 'publicaciones agotadas')}.`, to: `${base}/inventario?stock=agotado`, cta: 'Reponer' })
  }
  if (data.inventory && data.inventory.lowStock > 0 && can('PRODUCT_READ')) {
    items.push({ key: 'low', icon: AlertTriangleIcon, text: `${formatNumber(data.inventory.lowStock)} con stock bajo (1 a 3 unidades).`, to: `${base}/inventario?stock=bajo`, cta: 'Revisar' })
  }

  return (
    <section aria-labelledby="pendientes-titulo" className="flex flex-col gap-3">
      <h2 id="pendientes-titulo" className="text-lg font-extrabold tracking-tight">
        Para hacer hoy
      </h2>
      {items.length === 0 ? (
        <p className="rounded-lg bg-card p-4 text-sm text-muted-foreground ring-1 ring-foreground/10">Todo al día: no hay pedidos ni existencias que atender.</p>
      ) : (
        <ul className="divide-y divide-border rounded-xl bg-card ring-1 ring-foreground/10">
          {items.map((item) => (
            <li key={item.key} className="flex flex-wrap items-center gap-3 p-3 sm:px-4">
              <item.icon className="size-5 shrink-0 text-muted-foreground" aria-hidden />
              <p className="min-w-0 flex-1 text-sm font-medium">{item.text}</p>
              <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={item.to} />}>
                {item.cta}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** Resumen de 7 días: ventas diarias y embudo, con la misma analítica de «Ventas». */
function SalesGlance({ base }: { base: string }) {
  const sales = useStoreQuery('sales:7', async (id) => (await getSalesService()).sales(id, 7))
  if (sales.status === 'error' || !sales.data || sales.data.totals.orders === 0) return null
  const data = sales.data
  return (
    <section aria-labelledby="semana-titulo" className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <h2 id="semana-titulo" className="text-lg font-extrabold tracking-tight">
          Últimos 7 días
        </h2>
        <Link to={`${base}/ventas`} className="text-sm font-semibold underline-offset-4 hover:underline">
          Ver todas las ventas
        </Link>
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <BentoTile title="Ventas diarias" aside={formatCOP(data.totals.revenue)}>
          <Sparkline
            label="Ventas diarias"
            formatValue={formatCOP}
            points={data.daily.map((point) => ({ label: point.date, value: point.revenue }))}
          />
        </BentoTile>
        <BentoTile index={1} title="Embudo de pedidos">
          <OrderFunnel funnel={data.funnel} cancelled={data.cancelled} />
        </BentoTile>
      </div>
    </section>
  )
}

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

      <PendingActions data={data} />

      <section aria-labelledby="ventas-titulo" className="flex flex-col gap-3">
        <h2 id="ventas-titulo" className="text-lg font-extrabold tracking-tight">
          Ventas de este mes
        </h2>
        <RevealGroup className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Ingresos" value={<AnimatedNumber value={data.sales.revenueThisMonth} format={formatCOP} />} hint="Pedidos no cancelados" />
          <StatCard label="Pedidos" value={<AnimatedNumber value={data.sales.ordersThisMonth} format={formatNumber} />} hint="Recibidos este mes" to={can('ORDER_READ') ? `${base}/pedidos` : undefined} />
          <StatCard
            label="Por atender"
            value={<AnimatedNumber value={data.sales.pendingOrders} format={formatNumber} />}
            hint={data.sales.pendingOrders > 0 ? 'Confirma o cancela cada pedido' : 'No hay pedidos nuevos'}
            tone={data.sales.pendingOrders > 0 ? 'attention' : 'default'}
            to={can('ORDER_READ') ? `${base}/pedidos?estado=PENDING` : undefined}
          />
          <StatCard
            label="En preparación"
            value={<AnimatedNumber value={data.sales.preparingOrders} format={formatNumber} />}
            hint="Listos para registrar el envío"
            to={can('ORDER_READ') ? `${base}/pedidos?estado=PREPARING` : undefined}
          />
        </RevealGroup>
      </section>

      {can('ORDER_READ') ? <SalesGlance base={base} /> : null}

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
                to={can('PRODUCT_READ') ? `${base}/inventario?stock=agotado` : undefined}
              />
              <StatCard
                label="Stock bajo"
                value={formatNumber(data.inventory.lowStock)}
                hint="De 1 a 3 unidades"
                tone={data.inventory.lowStock > 0 ? 'attention' : 'default'}
                to={can('PRODUCT_READ') ? `${base}/inventario?stock=bajo` : undefined}
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
