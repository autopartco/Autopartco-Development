import { Link } from 'react-router'
import { ArrowRightIcon } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP } from '@/lib/format'
import type { AsyncResult } from '@/lib/use-async'
import { StatCard } from '@/features/portal/components/stat-card'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { formatDate } from '@/features/portal/lib/labels'
import { AdminPage } from '../components/admin-kit'
import { useAdminManufacturers, useAdminPlans, useAdminPromotions, usePendingApplications } from '../hooks'
import { ADMIN_SECTION_GROUPS } from '../lib/sections'
import { timeOf } from '../lib/labels'

/** Valor de una cifra: esqueleto mientras carga, guion si falló (cada tarjeta es independiente). */
function figure<T>(result: AsyncResult<T>, pick: (data: T) => number | string) {
  if (result.data !== undefined) return pick(result.data)
  if (result.status === 'error') return '—'
  // `<span>`: la cifra va dentro del `<p>` de `StatCard`.
  return (
    <span className="inline-block h-8 w-14 animate-pulse rounded-md bg-muted">
      <span className="sr-only">Cargando</span>
    </span>
  )
}

function Panel({ title, to, linkLabel, children }: { title: string; to: string; linkLabel: string; children: React.ReactNode }) {
  return (
    <Card className="gap-3 p-5 ring-1 ring-foreground/10">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-base font-extrabold tracking-tight">{title}</h2>
        <Link to={to} className="inline-flex min-h-11 items-center gap-1 text-sm font-semibold hover:underline">
          {linkLabel}
          <ArrowRightIcon className="size-4" aria-hidden />
        </Link>
      </div>
      {children}
    </Card>
  )
}

/** `/admin/resumen`: pendientes de la plataforma y accesos a cada sección de la consola. */
export default function AdminOverviewPage() {
  const applications = usePendingApplications()
  const plans = useAdminPlans()
  const promotions = useAdminPromotions()
  const manufacturers = useAdminManufacturers()

  const oldest = [...(applications.data ?? [])].sort((a, b) => timeOf(a.createdAt) - timeOf(b.createdAt)).slice(0, 5)
  const suggested = (manufacturers.data ?? []).filter((item) => !item.approved).slice(0, 5)
  const live = (promotions.data ?? []).filter((item) => item.currentlyActive)

  return (
    <AdminPage label="Plataforma" title="Resumen" description="Lo que necesita atención en Autopartco y el estado de la oferta comercial.">
      <div className="flex flex-col gap-8">
        <section aria-label="Cifras clave" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatCard
            label="Solicitudes por revisar"
            value={figure(applications, (data) => data.length)}
            hint="Almacenes esperando verificación"
            to="/admin/solicitudes"
            tone={(applications.data?.length ?? 0) > 0 ? 'attention' : 'default'}
          />
          <StatCard
            label="Fabricantes por aprobar"
            value={figure(manufacturers, (data) => data.filter((item) => !item.approved).length)}
            hint="Sugeridos por almacenes"
            to="/admin/catalogo?vista=fabricantes"
            tone={suggested.length > 0 ? 'attention' : 'default'}
          />
          <StatCard label="Promociones vigentes" value={figure(promotions, (data) => data.filter((item) => item.currentlyActive).length)} hint="De plataforma" to="/admin/promociones" />
          <StatCard label="Planes disponibles" value={figure(plans, (data) => data.filter((item) => item.enabled).length)} hint={plans.data ? `de ${plans.data.length} creados` : undefined} to="/admin/planes" />
        </section>

        <div className="grid gap-4 lg:grid-cols-2">
          <Panel title="Solicitudes más antiguas" to="/admin/solicitudes" linkLabel="Revisar">
            {applications.data === undefined ? (
              applications.status === 'error' ? <p className="text-sm text-muted-foreground">No pudimos cargar las solicitudes.</p> : <Skeleton className="h-24" />
            ) : oldest.length === 0 ? (
              <p className="text-sm text-muted-foreground">No hay solicitudes pendientes.</p>
            ) : (
              <ul className="flex flex-col divide-y divide-border">
                {oldest.map((item) => (
                  <li key={item.applicationId} className="flex items-center justify-between gap-3 py-2.5">
                    <div className="min-w-0">
                      <p className="truncate font-semibold">{item.businessName}</p>
                      <p className="text-sm text-muted-foreground">NIT {item.nit}</p>
                    </div>
                    <span className="shrink-0 text-sm text-muted-foreground tabular-nums">{formatDate(item.createdAt)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="Fabricantes sugeridos" to="/admin/catalogo?vista=fabricantes" linkLabel="Ver catálogo">
            {manufacturers.data === undefined ? (
              manufacturers.status === 'error' ? <p className="text-sm text-muted-foreground">No pudimos cargar los fabricantes.</p> : <Skeleton className="h-24" />
            ) : suggested.length === 0 ? (
              <p className="text-sm text-muted-foreground">No hay fabricantes por aprobar.</p>
            ) : (
              <ul className="flex flex-col divide-y divide-border">
                {suggested.map((item) => (
                  <li key={item.id} className="flex items-center justify-between gap-3 py-2.5">
                    <p className="truncate font-semibold">{item.name}</p>
                    <StatusBadge tone="warning">Por aprobar</StatusBadge>
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="Promociones vigentes" to="/admin/promociones" linkLabel="Gestionar">
            {promotions.data === undefined ? (
              promotions.status === 'error' ? <p className="text-sm text-muted-foreground">No pudimos cargar las promociones.</p> : <Skeleton className="h-24" />
            ) : live.length === 0 ? (
              <p className="text-sm text-muted-foreground">Ninguna promoción de plataforma está activa ahora.</p>
            ) : (
              <ul className="flex flex-col divide-y divide-border">
                {live.slice(0, 5).map((item) => (
                  <li key={item.id} className="flex items-center justify-between gap-3 py-2.5">
                    <div className="min-w-0">
                      <p className="truncate font-semibold">{item.name}</p>
                      <p className="text-sm text-muted-foreground">Hasta {formatDate(item.endsAt)}</p>
                    </div>
                    <span className="shrink-0 font-semibold tabular-nums">
                      {item.discountType === 'PERCENTAGE' ? `${item.discountValue} %` : formatCOP(item.discountValue)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>

          <Panel title="Planes" to="/admin/planes" linkLabel="Editar planes">
            {plans.data === undefined ? (
              plans.status === 'error' ? <p className="text-sm text-muted-foreground">No pudimos cargar los planes.</p> : <Skeleton className="h-24" />
            ) : (
              <ul className="flex flex-col divide-y divide-border">
                {plans.data.map((item) => (
                  <li key={item.id} className="flex items-center justify-between gap-3 py-2.5">
                    <div className="flex min-w-0 items-center gap-2">
                      <p className="truncate font-semibold">{item.name}</p>
                      {!item.enabled ? <StatusBadge>Deshabilitado</StatusBadge> : null}
                    </div>
                    <span className="shrink-0 tabular-nums">{item.monthlyPriceCop === 0 ? 'Gratis' : `${formatCOP(item.monthlyPriceCop)}/mes`}</span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </div>

        <section aria-labelledby="admin-atajos" className="flex flex-col gap-3">
          <h2 id="admin-atajos" className="label-mono text-muted-foreground">
            Secciones
          </h2>
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {ADMIN_SECTION_GROUPS.flatMap((group) => group.items)
              .filter((item) => item.to !== 'resumen')
              .map((item) => (
                <li key={item.to}>
                  <Link
                    to={`/admin/${item.to}`}
                    className="flex h-full items-start gap-3 rounded-xl bg-card p-4 ring-1 ring-foreground/10 transition-[box-shadow,transform] duration-200 hover:ring-foreground/30 motion-safe:hover:-translate-y-0.5"
                  >
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-asphalt text-white">
                      <item.icon className="size-5" aria-hidden />
                    </span>
                    <span className="flex flex-col gap-0.5">
                      <span className="font-semibold">{item.label}</span>
                      <span className="text-sm text-muted-foreground">{item.description}</span>
                    </span>
                  </Link>
                </li>
              ))}
          </ul>
        </section>
      </div>
    </AdminPage>
  )
}
