import { Link } from 'react-router'
import { PackageIcon, PlusIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { formatCOP, formatNumber, pluralize } from '@/lib/format'
import { getAlertService } from '../api/alert-service'
import type { StoreAlert } from '../api/alert-types'
import { getDashboardService } from '../api/dashboard-service'
import type { Dashboard, StorePermission } from '../api/types'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { UsageMeter } from '../components/usage-meter'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { alertTarget, SEVERITY_LABEL } from '../lib/alerts'
import { formatDate, formatDateTime, formatDays, SUBSCRIPTION_STATUS_LABEL, SUBSCRIPTION_STATUS_TONE, type Tone } from '../lib/labels'

interface TodayItem {
  key: string
  /** Cifra que da tamaño a la tarea; sin cifra (p. ej. el plan) la columna queda vacía. */
  count?: number
  title: string
  detail: string
  tone: Tone
  to: string
  cta: string
}

/** Lo que pide acción hoy, de más a menos urgente. Solo lo que el servidor reporta y con cifra mayor que cero. */
function todayItems(data: Dashboard, base: string, can: (permission: StorePermission) => boolean): TodayItem[] {
  const items: TodayItem[] = []
  if (data.subscriptionStatus === 'EXPIRED') {
    items.push({ key: 'plan', title: 'Suscripción vencida', detail: 'Renuévala para volver a crear y publicar productos.', tone: 'danger', to: `${base}/plan`, cta: 'Renovar plan' })
  } else if (data.daysRemaining !== null && data.daysRemaining !== undefined && data.daysRemaining <= 7) {
    items.push({ key: 'plan', title: `El plan vence en ${formatDays(data.daysRemaining)}`, detail: 'Renueva o elige un plan para no perder funciones.', tone: 'warning', to: `${base}/plan`, cta: 'Ver plan' })
  }
  if (can('ORDER_READ') && data.sales.pendingOrders > 0) {
    const count = data.sales.pendingOrders
    items.push({ key: 'pending', count, title: pluralize(count, 'Pedido por confirmar', 'Pedidos por confirmar'), detail: 'El comprador espera tu confirmación o cancelación.', tone: 'warning', to: `${base}/pedidos?estado=PENDING`, cta: 'Confirmar pedidos' })
  }
  if (can('ORDER_READ') && data.sales.preparingOrders > 0) {
    const count = data.sales.preparingOrders
    items.push({ key: 'preparing', count, title: pluralize(count, 'Pedido por despachar', 'Pedidos por despachar'), detail: 'Están en preparación: registra el envío cuando salgan.', tone: 'info', to: `${base}/pedidos?estado=PREPARING`, cta: 'Registrar envíos' })
  }
  if (can('PRODUCT_READ') && data.inventory && data.inventory.outOfStock > 0) {
    const count = data.inventory.outOfStock
    items.push({ key: 'out', count, title: pluralize(count, 'Publicación agotada', 'Publicaciones agotadas'), detail: 'No se pueden vender hasta que repongas existencias.', tone: 'danger', to: `${base}/inventario?stock=agotado`, cta: 'Reponer' })
  }
  if (can('PRODUCT_READ') && data.inventory && data.inventory.lowStock > 0) {
    const count = data.inventory.lowStock
    items.push({ key: 'low', count, title: pluralize(count, 'Publicación con stock bajo', 'Publicaciones con stock bajo'), detail: 'Quedan de 1 a 3 unidades.', tone: 'warning', to: `${base}/inventario?stock=bajo`, cta: 'Revisar stock' })
  }
  return items
}

const TONE_TEXT: Record<Tone, string> = {
  danger: 'text-ap-danger',
  warning: 'text-ap-warning',
  info: 'text-ap-info',
  success: 'text-ap-success',
  neutral: 'text-ap-ink',
}

/** Filas accionables: cifra a la izquierda (tabular), qué pasa, y la acción que lo resuelve. */
function TodayList({ data }: { data: Dashboard }) {
  const { storeId, can } = usePortal()
  const items = todayItems(data, `/portal/${encodeURIComponent(storeId)}`, can)

  return (
    <section aria-labelledby="hoy-titulo" className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Inicio / Hoy</p>
        <h2 id="hoy-titulo" className="text-block-title text-ap-ink">
          Qué atender hoy
        </h2>
      </div>
      {items.length === 0 ? (
        <p className="rounded-lg border border-ap-line px-4 py-5 text-sm text-ap-muted">
          <strong className="font-semibold text-ap-ink">Todo al día.</strong> No hay pedidos por confirmar o despachar ni publicaciones agotadas.
        </p>
      ) : (
        <ul className="divide-y divide-ap-line rounded-lg border border-ap-line">
          {items.map((item) => (
            <li key={item.key} className="grid grid-cols-[3.5rem_minmax(0,1fr)] items-center gap-x-3 gap-y-3 px-3 py-3 sm:grid-cols-[4rem_minmax(0,1fr)_auto] sm:px-4">
              <p className={`ap-price text-right text-2xl leading-none ${TONE_TEXT[item.tone]}`} aria-hidden={item.count === undefined}>
                {item.count !== undefined ? formatNumber(item.count) : '—'}
              </p>
              <div className="min-w-0">
                <p className="font-semibold text-ap-ink">
                  {item.count !== undefined ? <span className="sr-only">{formatNumber(item.count)} </span> : null}
                  {item.title}
                </p>
                <p className="text-sm text-ap-muted">{item.detail}</p>
              </div>
              <Button variant="outline" className="col-span-2 h-11 px-4 sm:col-span-1" nativeButton={false} render={<Link to={item.to} />}>
                {item.cta}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** Alertas abiertas (misma caché que el panel de la campana): gravedad, título y a dónde ir. */
function OpenAlerts() {
  const { storeId, can } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const alerts = useStoreQuery('alerts:list:open', async (id) => (await getAlertService()).list(id, 'open'))

  return (
    <section aria-labelledby="alertas-titulo" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="alertas-titulo" className="text-base font-semibold text-ap-ink">
          Alertas abiertas
          {alerts.data && alerts.data.length > 0 ? <span className="ml-2 font-normal text-ap-muted tabular-nums">{formatNumber(alerts.data.length)}</span> : null}
        </h2>
        <Link to={`${base}/alertas`} className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
          Ver todas las alertas
        </Link>
      </div>
      <QueryBoundary result={alerts} what="las alertas" skeleton={<ListSkeleton rows={2} label="Cargando las alertas" />}>
        {(rows) =>
          rows.length === 0 ? (
            <p className="rounded-lg border border-ap-line px-4 py-5 text-sm text-ap-muted">Sin alertas abiertas. Te avisaremos aquí si una venta cae o un producto se agota.</p>
          ) : (
            <ul className="divide-y divide-ap-line rounded-lg border border-ap-line">
              {rows.slice(0, 4).map((alert) => (
                <AlertRow key={alert.id} alert={alert} to={alertTarget(alert, storeId, can)?.to ?? `${base}/alertas`} />
              ))}
            </ul>
          )
        }
      </QueryBoundary>
    </section>
  )
}

function AlertRow({ alert, to }: { alert: StoreAlert; to: string }) {
  const severity = SEVERITY_LABEL[alert.severity]
  const unread = alert.readAt === null
  return (
    <li className={unread ? 'shadow-[inset_3px_0_0_var(--ap-red)]' : undefined}>
      <Link to={to} className="flex flex-col gap-1 px-4 py-3 hover:bg-ap-paper sm:flex-row sm:items-center sm:gap-3">
        <StatusBadge tone={severity.tone} className="shrink-0">
          {severity.label}
        </StatusBadge>
        <span className={`min-w-0 flex-1 text-sm text-ap-ink ${unread ? 'font-semibold' : 'font-medium'}`}>
          {alert.title}
          {unread ? <span className="sr-only"> (sin leer)</span> : null}
        </span>
        <time dateTime={alert.createdAt} className="ap-ref shrink-0 text-ap-muted">
          {formatDateTime(alert.createdAt)}
        </time>
      </Link>
    </li>
  )
}

/** Dos cifras del mes (no un tablero): sirven para saber si conviene revisar Ventas. */
function MonthSales({ data }: { data: Dashboard }) {
  const { storeId, can } = usePortal()
  return (
    <section aria-labelledby="mes-titulo" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
      <h2 id="mes-titulo" className="text-base font-semibold text-ap-ink">
        Este mes
      </h2>
      <dl className="grid grid-cols-2 gap-4">
        <div className="flex flex-col gap-0.5">
          <dt className="text-sm text-ap-muted">Ingresos</dt>
          <dd className="ap-price text-xl text-ap-ink">{formatCOP(data.sales.revenueThisMonth)}</dd>
        </div>
        <div className="flex flex-col gap-0.5">
          <dt className="text-sm text-ap-muted">Pedidos</dt>
          <dd className="ap-price text-xl text-ap-ink">{formatNumber(data.sales.ordersThisMonth)}</dd>
        </div>
      </dl>
      <p className="text-xs text-ap-muted">Ingresos de pedidos no cancelados.</p>
      {can('ORDER_READ') ? (
        <Link to={`/portal/${encodeURIComponent(storeId)}/ventas`} className="inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
          Ver ventas y tendencias
        </Link>
      ) : null}
    </section>
  )
}

function PlanStatus({ data }: { data: Dashboard }) {
  const { storeId } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const status = data.subscriptionStatus
  const days = data.daysRemaining
  const until = status === 'ACTIVE' ? data.paidThrough : data.trialEndsAt
  return (
    <section aria-labelledby="plan-titulo" className="flex flex-col gap-4 border-t border-ap-line-strong pt-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="plan-titulo" className="text-base font-semibold text-ap-ink">
          Plan {data.plan.name}
        </h2>
        {status ? <StatusBadge tone={SUBSCRIPTION_STATUS_TONE[status]}>{SUBSCRIPTION_STATUS_LABEL[status]}</StatusBadge> : null}
      </div>
      <p className="-mt-2 text-sm text-ap-muted">
        {status === 'EXPIRED'
          ? 'Tu suscripción venció.'
          : until
            ? `Vigente hasta ${formatDate(until)}${days !== null && days !== undefined ? ` (${formatDays(days)})` : ''}.`
            : 'Sin fecha de vigencia.'}
      </p>
      <UsageMeter capacity="PRODUCTS" used={data.usage.products} limit={data.maxProducts} upgradeTo={`${base}/plan`} />
      <UsageMeter capacity="BRANCHES" used={data.usage.branches} limit={data.maxBranches} upgradeTo={`${base}/plan`} />
      <UsageMeter capacity="USERS" used={data.usage.users} limit={data.maxUsers} upgradeTo={`${base}/plan`} />
      <Link to={`${base}/plan`} className="inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
        Ver plan y suscripción
      </Link>
    </section>
  )
}

function DashboardView({ data }: { data: Dashboard }) {
  const { storeId, can } = usePortal()
  const emptyCatalog = data.usage.products === 0

  return (
    <div className="flex flex-col gap-6">
      {data.setupRequired ? (
        <p role="status" className="rounded-lg border border-ap-warning/30 bg-ap-warning-soft p-4 text-sm text-ap-ink">
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
            <Button className="h-11 px-5" nativeButton={false} render={<Link to={`/portal/${encodeURIComponent(storeId)}/productos/nuevo`} />}>
              <PlusIcon aria-hidden />
              Agregar producto
            </Button>
          ) : null}
        </EmptyState>
      ) : null}

      <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="flex min-w-0 flex-col gap-8">
          <TodayList data={data} />
          {can('STORE_READ') ? <OpenAlerts /> : null}
        </div>
        <aside aria-label="Contexto del almacén" className="flex flex-col gap-6">
          <MonthSales data={data} />
          <PlanStatus data={data} />
        </aside>
      </div>
    </div>
  )
}

/** `/portal/:storeId/inicio`: qué atender hoy (pedidos, existencias, alertas) y, al lado, el mes y el plan. */
export default function PortalOverviewPage() {
  const { store, can, storeId } = usePortal()
  const dashboard = useStoreQuery('dashboard', async (id) => (await getDashboardService()).dashboard(id))
  const name = dashboard.data?.storeName ?? store?.businessName

  return (
    <>
      <title>{`Inicio | ${name ?? 'Portal'} | Autopartco`}</title>
      <PageHeader
        title="Inicio"
        description={name ? `Lo que necesita tu atención en ${name}.` : 'Lo que necesita tu atención en el almacén.'}
        actions={
          can('PRODUCT_CREATE') ? (
            <Button className="h-11 px-4" nativeButton={false} render={<Link to={`/portal/${encodeURIComponent(storeId)}/productos/nuevo`} />}>
              <PlusIcon aria-hidden />
              Agregar producto
            </Button>
          ) : null
        }
      />
      <QueryBoundary result={dashboard} what="el resumen del almacén" skeleton={<ListSkeleton rows={4} label="Cargando el resumen" />}>
        {(data) => <DashboardView data={data} />}
      </QueryBoundary>
    </>
  )
}
