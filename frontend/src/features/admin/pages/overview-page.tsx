import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { RotateCwIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP, formatNumber, pluralize } from '@/lib/format'
import type { AsyncResult } from '@/lib/use-async'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { formatDate, type Tone } from '@/features/portal/lib/labels'
import type { StoreApplication } from '../api/types'
import { AdminPage, AdminQueryError, AdminSection } from '../components/admin-kit'
import { useAdminManufacturers, useAdminPlans, useAdminPromotions, usePendingApplications } from '../hooks'
import { DAY_MS, startOfDay, timeOf } from '../lib/labels'

const TONE_TEXT: Record<Tone, string> = {
  danger: 'text-ap-danger',
  warning: 'text-ap-warning',
  info: 'text-ap-info',
  success: 'text-ap-success',
  neutral: 'text-ap-ink',
}

/** Días completos desde el envío (0 = hoy). */
function daysWaiting(createdAt: string): number {
  return Math.max(0, Math.round((startOfDay(Date.now()) - startOfDay(timeOf(createdAt))) / DAY_MS))
}

const waitingLabel = (days: number) => (days === 0 ? 'Enviada hoy' : `Espera hace ${pluralize(days, 'día', 'días')}`)

interface ReviewItem {
  key: string
  result: AsyncResult<unknown>
  what: string
  count?: number
  title: string
  detail: string
  tone: Tone
  to: string
  cta: string
}

/**
 * Una fila de «Qué revisar»: cifra a la izquierda, qué pasa y la acción que lo resuelve. Cada fila depende
 * de su propia consulta: carga, error con reintento y valor real son independientes.
 */
function ReviewRow({ item }: { item: ReviewItem }) {
  const { result } = item
  if (result.status === 'error' && result.data === undefined) {
    return (
      <li className="flex flex-col items-start gap-2 px-3 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-4">
        <p className="text-sm text-ap-danger">
          <strong className="font-semibold">No pudimos cargar {item.what}.</strong> Reintenta para saber si hay algo por revisar.
        </p>
        <Button variant="outline" className="h-11 px-4" onClick={result.retry}>
          <RotateCwIcon aria-hidden />
          Reintentar
        </Button>
      </li>
    )
  }
  if (result.data === undefined) {
    return (
      <li className="grid grid-cols-[3.5rem_minmax(0,1fr)] items-center gap-3 px-3 py-3 sm:px-4" role="status">
        <span className="sr-only">Cargando {item.what}…</span>
        <Skeleton aria-hidden className="ml-auto h-7 w-8" />
        <Skeleton aria-hidden className="h-9 w-2/3" />
      </li>
    )
  }
  return (
    <li className="grid grid-cols-[3.5rem_minmax(0,1fr)] items-center gap-x-3 gap-y-3 px-3 py-3 sm:grid-cols-[4rem_minmax(0,1fr)_auto] sm:px-4">
      <p className={`ap-price text-right text-2xl leading-none ${TONE_TEXT[item.tone]}`} aria-hidden>
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
  )
}

/** Cola de revisión de la plataforma (§7.4): lo que espera una decisión del administrador, de más a menos urgente. */
function ReviewQueue({ items, settled }: { items: ReviewItem[]; settled: boolean }) {
  return (
    <section aria-labelledby="revisar-titulo" className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Resumen / Pendientes</p>
        <h2 id="revisar-titulo" className="text-block-title text-ap-ink">
          Qué revisar
        </h2>
      </div>
      {settled && items.length === 0 ? (
        <p className="rounded-lg border border-ap-line px-4 py-5 text-sm text-ap-muted">
          <strong className="font-semibold text-ap-ink">Todo al día.</strong> No hay solicitudes de almacenes ni fabricantes sugeridos esperando revisión.
        </p>
      ) : (
        <ul className="divide-y divide-ap-line rounded-lg border border-ap-line">
          {items.map((item) => (
            <ReviewRow key={item.key} item={item} />
          ))}
        </ul>
      )}
    </section>
  )
}

/** Las solicitudes que más tiempo llevan esperando, con NIT y fecha en mono. */
function OldestApplications({ result }: { result: AsyncResult<StoreApplication[]> }) {
  const oldest = [...(result.data ?? [])].sort((a, b) => timeOf(a.createdAt) - timeOf(b.createdAt)).slice(0, 5)
  return (
    <AdminSection
      id="cola-titulo"
      title="Solicitudes que más esperan"
      meta="Orden: fecha de envío, de la más antigua a la más reciente"
      actions={
        <Link to="/admin/solicitudes" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
          Ver la cola completa
        </Link>
      }
    >
      {result.data === undefined ? (
        result.status === 'error' ? (
          <AdminQueryError error={result.error} what="las solicitudes" onRetry={result.retry} />
        ) : (
          <div role="status" className="flex flex-col gap-2">
            <span className="sr-only">Cargando las solicitudes…</span>
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} aria-hidden className="h-12" />
            ))}
          </div>
        )
      ) : oldest.length === 0 ? (
        <p className="border-l-2 border-ap-line-strong py-1 pl-3 text-sm text-ap-muted">No hay solicitudes en espera. Las nuevas aparecerán aquí por orden de llegada.</p>
      ) : (
        <ol className="divide-y divide-ap-line border-y border-ap-line">
          {oldest.map((item) => {
            const days = daysWaiting(item.createdAt)
            return (
              <li key={item.applicationId}>
                <Link
                  to="/admin/solicitudes"
                  state={{ open: item.applicationId }}
                  className="grid gap-x-4 gap-y-0.5 px-1 py-3 hover:bg-ap-paper sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-semibold text-ap-ink">{item.businessName}</span>
                    <span className="ap-ref text-ap-muted">NIT {item.nit}</span>
                  </span>
                  <span className="flex flex-col sm:items-end">
                    <time dateTime={item.createdAt} className="ap-ref text-ap-ink">
                      {formatDate(item.createdAt)}
                    </time>
                    <span className={days >= 3 ? 'text-xs font-medium text-ap-warning' : 'text-xs text-ap-muted'}>{waitingLabel(days)}</span>
                  </span>
                </Link>
              </li>
            )
          })}
        </ol>
      )}
    </AdminSection>
  )
}

/** Bloque de contexto lateral: lista corta con su estado de carga y error. */
function SideList<T>({ id, title, result, what, empty, to, linkLabel, children }: { id: string; title: string; result: AsyncResult<T[]>; what: string; empty: string; to: string; linkLabel: string; children: (rows: T[]) => ReactNode }) {
  return (
    <AdminSection id={id} title={title}>
      {result.data === undefined ? (
        result.status === 'error' ? (
          <AdminQueryError error={result.error} what={what} onRetry={result.retry} className="p-4" />
        ) : (
          <Skeleton className="h-20">
            <span className="sr-only">Cargando {what}…</span>
          </Skeleton>
        )
      ) : result.data.length === 0 ? (
        <p className="text-sm text-ap-muted">{empty}</p>
      ) : (
        children(result.data)
      )}
      <Link to={to} className="inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
        {linkLabel}
      </Link>
    </AdminSection>
  )
}

/** `/admin/resumen`: qué espera revisión (solicitudes, fabricantes, planes) y, al lado, la oferta comercial vigente. */
export default function AdminOverviewPage() {
  const applications = usePendingApplications()
  const plans = useAdminPlans()
  const promotions = useAdminPromotions()
  const manufacturers = useAdminManufacturers()

  const pendingApplications = applications.data ?? []
  const oldestDays = pendingApplications.length > 0 ? Math.max(...pendingApplications.map((item) => daysWaiting(item.createdAt))) : 0
  const suggested = (manufacturers.data ?? []).filter((item) => !item.approved)
  const enabledPlans = (plans.data ?? []).filter((item) => item.enabled)
  const live = (promotions.data ?? []).filter((item) => item.currentlyActive)

  // Solo filas con algo por hacer; mientras carga o si falla, la fila se queda para mostrar ese estado.
  const candidates: (ReviewItem & { show: boolean })[] = [
    {
      key: 'applications',
      result: applications,
      what: 'las solicitudes de almacenes',
      count: pendingApplications.length,
      title: pendingApplications.length === 1 ? 'Solicitud de almacén por revisar' : 'Solicitudes de almacenes por revisar',
      detail: `Documentos por verificar antes de que el almacén pueda vender. La más antigua: ${waitingLabel(oldestDays).toLowerCase()}.`,
      tone: oldestDays >= 3 ? 'warning' : 'info',
      to: '/admin/solicitudes',
      cta: 'Revisar solicitudes',
      show: applications.data === undefined || pendingApplications.length > 0,
    },
    {
      key: 'manufacturers',
      result: manufacturers,
      what: 'los fabricantes',
      count: suggested.length,
      title: suggested.length === 1 ? 'Fabricante sugerido por aprobar' : 'Fabricantes sugeridos por aprobar',
      detail: 'Los propusieron almacenes al publicar repuestos. Apruébalos o desactívalos desde el catálogo.',
      tone: 'warning',
      to: '/admin/catalogo?vista=fabricantes',
      cta: 'Revisar fabricantes',
      show: manufacturers.data === undefined || suggested.length > 0,
    },
    {
      key: 'plans',
      result: plans,
      what: 'los planes',
      title: 'Ningún plan está disponible',
      detail: 'Los almacenes nuevos no pueden elegir plan. Habilita o crea al menos uno.',
      tone: 'danger',
      to: '/admin/planes',
      cta: 'Ir a planes',
      show: plans.data === undefined || enabledPlans.length === 0,
    },
  ]
  const items = candidates.filter((item) => item.show)
  const settled = applications.data !== undefined && manufacturers.data !== undefined && plans.data !== undefined

  return (
    <AdminPage
      title="Resumen"
      description="Lo que espera una decisión de administración y la oferta comercial vigente en Autopartco."
      actions={
        pendingApplications.length > 0 ? (
          <Button className="h-11 px-4" nativeButton={false} render={<Link to="/admin/solicitudes" />}>
            Revisar solicitudes
          </Button>
        ) : null
      }
    >
      <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="flex min-w-0 flex-col gap-8">
          <ReviewQueue items={items} settled={settled} />
          <OldestApplications result={applications} />
        </div>
        <aside aria-label="Oferta comercial vigente" className="flex flex-col gap-6">
          <SideList
            id="promos-titulo"
            title="Promociones vigentes"
            result={{ ...promotions, data: promotions.data ? live : undefined }}
            what="las promociones"
            empty="Ninguna promoción de plataforma está activa ahora."
            to="/admin/promociones"
            linkLabel="Gestionar promociones"
          >
            {(rows) => (
              <ul className="divide-y divide-ap-line border-y border-ap-line">
                {rows.slice(0, 5).map((item) => (
                  <li key={item.id} className="flex items-start justify-between gap-3 py-2.5">
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold text-ap-ink">{item.name}</span>
                      <span className="ap-ref text-ap-muted">Hasta {formatDate(item.endsAt)}</span>
                    </span>
                    <span className="ap-price shrink-0 text-sm text-ap-ink">
                      {item.discountType === 'PERCENTAGE' ? `${formatNumber(item.discountValue)} %` : formatCOP(item.discountValue)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </SideList>
          <SideList id="planes-titulo" title="Planes" result={plans} what="los planes" empty="Aún no hay planes creados." to="/admin/planes" linkLabel="Editar planes">
            {(rows) => (
              <ul className="divide-y divide-ap-line border-y border-ap-line">
                {rows.map((item) => (
                  <li key={item.id} className="flex items-center justify-between gap-3 py-2.5">
                    <span className="flex min-w-0 items-center gap-2">
                      <span className="truncate text-sm font-semibold text-ap-ink">{item.name}</span>
                      {!item.enabled ? <StatusBadge>Deshabilitado</StatusBadge> : null}
                    </span>
                    <span className="ap-num shrink-0 text-sm text-ap-ink">{item.monthlyPriceCop === 0 ? 'Gratis' : `${formatCOP(item.monthlyPriceCop)}/mes`}</span>
                  </li>
                ))}
              </ul>
            )}
          </SideList>
        </aside>
      </div>
    </AdminPage>
  )
}
