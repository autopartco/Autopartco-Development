import { useSearchParams } from 'react-router'
import { Card } from '@/components/ui/card'
import { formatCOP, formatNumber } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { AnalyticsChart, type ChartPoint } from '@/features/portal/components/analytics-chart'
import { SelectField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { QueryBoundary } from '@/features/portal/components/query-boundary'
import { StatCard } from '@/features/portal/components/stat-card'
import { GRANULARITY_OPTIONS, RANGE_PRESETS, isGranularity, isRangePreset, periodLabel, resolveRange, type RangePreset } from '@/features/portal/lib/analytics'
import { getAdminInsightsService } from '../api/admin-insights-service'
import type { StatCount, StatsGranularity } from '../api/insights-types'
import { AdminPage } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const PRESETS = RANGE_PRESETS.filter((preset) => preset.value !== 'personalizado')
const percent = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 1 })
const TYPE_LABEL: Record<string, string> = { CUSTOMER: 'Clientes', STORE_MANAGER: 'Almacenes', ROOT: 'Administración', ADMIN: 'Administración' }
const PROVIDER_LABEL: Record<string, string> = { google: 'Google', facebook: 'Facebook' }

function Section({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-3" aria-label={title}>
      <div>
        <h2 className="text-lg font-extrabold tracking-tight">{title}</h2>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {children}
    </section>
  )
}

/** Lista con barra proporcional (rutas, referentes, tipos de cuenta, planes). */
function BarList({ title, rows, empty, format = formatNumber, label = (key: string) => key }: { title: string; rows: StatCount[]; empty: string; format?: (value: number) => string; label?: (key: string) => string }) {
  const max = Math.max(1, ...rows.map((row) => row.count))
  return (
    <Card className="gap-3 p-5 ring-1 ring-foreground/10">
      <h3 className="font-bold">{title}</h3>
      {rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">{empty}</p>
      ) : (
        <ul className="flex flex-col gap-2.5">
          {rows.map((row) => (
            <li key={row.key} className="flex flex-col gap-1">
              <div className="flex items-baseline justify-between gap-3 text-sm">
                <span className="min-w-0 truncate font-medium">{label(row.key)}</span>
                <span className="shrink-0 tabular-nums text-muted-foreground">{format(row.count)}</span>
              </div>
              <div className="h-1.5 rounded-full bg-concrete" aria-hidden>
                <div className="h-full rounded-full bg-asphalt" style={{ width: `${(row.count / max) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function ChartCard({ title, points, label }: { title: string; points: ChartPoint[]; label: string }) {
  return (
    <Card className="gap-3 p-5 ring-1 ring-foreground/10">
      <h3 className="font-bold">{title}</h3>
      {points.length === 0 ? <p className="text-sm text-muted-foreground">Sin datos en este periodo.</p> : <AnalyticsChart mode="bars" points={points} label={label} formatAxis={(value) => formatNumber(Math.round(value))} />}
    </Card>
  )
}

/** `/admin/estadisticas`: tráfico, cuentas, accesos y comercio de toda la plataforma. */
export default function AdminStatsPage() {
  const [params, setParams] = useSearchParams()
  const preset: RangePreset = isRangePreset(params.get('rango')) && params.get('rango') !== 'personalizado' ? (params.get('rango') as RangePreset) : '30'
  const granularity: StatsGranularity = isGranularity(params.get('agrupar')) ? (params.get('agrupar') as StatsGranularity) : 'DAY'
  const range = resolveRange(preset, {})
  const report = useAsync(`${ADMIN_CACHE_PREFIX}stats:${range.from}:${range.to}:${granularity}`, async () =>
    (await getAdminInsightsService()).platformStats({ ...range, granularity }),
  )

  function update(name: string, value: string) {
    const next = new URLSearchParams(params)
    next.set(name, value)
    setParams(next, { replace: true })
  }

  const points = <T extends { date: string }>(series: T[], pick: (row: T) => number, noun: string): ChartPoint[] =>
    series.map((row) => {
      const label = periodLabel(row.date, granularity)
      return { label, detail: `${label}: ${formatNumber(pick(row))} ${noun}`, bar: pick(row) }
    })

  return (
    <AdminPage label="Inteligencia" title="Estadísticas" description="Tráfico del marketplace, cuentas, accesos y resultados comerciales de toda la plataforma.">
      <div className="mb-6 grid gap-3 sm:max-w-lg sm:grid-cols-2">
        <SelectField id="stats-rango" label="Periodo" options={PRESETS} value={preset} onChange={(event) => update('rango', event.target.value)} />
        <SelectField id="stats-agrupar" label="Agrupar" options={GRANULARITY_OPTIONS} value={granularity} onChange={(event) => update('agrupar', event.target.value)} />
      </div>
      <QueryBoundary result={report} what="las estadísticas" keepPrevious skeleton={<ListSkeleton rows={6} label="Cargando estadísticas" />}>
        {(data) => {
          const devicesTotal = Object.values(data.traffic.devices).reduce((sum, value) => sum + value, 0)
          const mobileShare = devicesTotal > 0 ? (data.traffic.devices.mobile ?? 0) / devicesTotal : 0
          const failedShare = data.access.logins + data.access.failedLogins > 0 ? data.access.failedLogins / (data.access.logins + data.access.failedLogins) : 0
          return (
            <div className="flex flex-col gap-10">
              <Section title="Tráfico" description="Visitas al marketplace (sin consola ni portal). Medición propia, sin datos personales.">
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                  <StatCard label="Visitas" value={formatNumber(data.traffic.visits)} />
                  <StatCard label="Visitantes únicos" value={formatNumber(data.traffic.uniqueVisitors)} hint="Distintos por día" />
                  <StatCard label="Desde celular" value={percent.format(mobileShare)} />
                  <StatCard label="Páginas por visitante" value={data.traffic.uniqueVisitors > 0 ? (data.traffic.visits / data.traffic.uniqueVisitors).toLocaleString('es-CO', { maximumFractionDigits: 1 }) : '—'} />
                </div>
                <ChartCard title="Visitas" label="Visitas por periodo" points={points(data.traffic.series, (row) => row.visits, 'visitas')} />
                <div className="grid gap-4 lg:grid-cols-2">
                  <BarList title="Páginas más vistas" rows={data.traffic.topPaths} empty="Aún no hay visitas registradas." />
                  <BarList title="De dónde llegan" rows={data.traffic.referrers} empty="Sin referentes externos en este periodo." />
                </div>
              </Section>

              <Section title="Cuentas">
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                  <StatCard label="Cuentas totales" value={formatNumber(data.accounts.total)} />
                  <StatCard label="Nuevas en el periodo" value={formatNumber(data.accounts.created)} />
                  <StatCard label="Activas (verificadas)" value={percent.format(data.accounts.verifiedRate)} />
                  <StatCard label="Bloqueadas" value={formatNumber(data.accounts.locked)} tone={data.accounts.locked > 0 ? 'attention' : 'default'} to="/admin/auditoria?accion=ACCOUNT_LOCKED" />
                </div>
                <ChartCard title="Cuentas nuevas" label="Cuentas nuevas por periodo" points={points(data.accounts.series, (row) => row.created, 'cuentas')} />
                <div className="grid gap-4 lg:grid-cols-2">
                  <BarList title="Por tipo" rows={data.accounts.byType} empty="Sin cuentas." label={(key) => TYPE_LABEL[key] ?? key} />
                  <BarList title="Vinculadas a redes" rows={data.accounts.socialShare} empty="Nadie ha ingresado con Google o Facebook todavía." label={(key) => PROVIDER_LABEL[key] ?? key} />
                </div>
              </Section>

              <Section title="Accesos" description="Usuarios activos: cuentas con al menos un ingreso correcto hasta el final del periodo.">
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
                  <StatCard label="Activos hoy (DAU)" value={formatNumber(data.access.dau)} />
                  <StatCard label="Activos 7 días (WAU)" value={formatNumber(data.access.wau)} />
                  <StatCard label="Activos 30 días (MAU)" value={formatNumber(data.access.mau)} />
                  <StatCard label="Ingresos" value={formatNumber(data.access.logins)} />
                  <StatCard
                    label="Ingresos fallidos"
                    value={formatNumber(data.access.failedLogins)}
                    hint={percent.format(failedShare)}
                    tone={failedShare > 0.2 ? 'attention' : 'default'}
                    to="/admin/auditoria?accion=LOGIN_FAILED"
                  />
                </div>
                <ChartCard title="Ingresos correctos" label="Ingresos por periodo" points={points(data.access.series, (row) => row.logins, 'ingresos')} />
              </Section>

              <Section title="Comercio">
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
                  <StatCard label="Pedidos válidos" value={formatNumber(data.commerce.orders)} />
                  <StatCard label="Ventas (GMV)" value={formatCOP(data.commerce.gmv)} />
                  <StatCard label="Ticket promedio" value={formatCOP(data.commerce.averageTicket)} />
                  <StatCard label="Almacenes activos" value={formatNumber(data.commerce.activeStores)} />
                  <StatCard label="Solicitudes por revisar" value={formatNumber(data.commerce.pendingApplications)} to="/admin/solicitudes" tone={data.commerce.pendingApplications > 0 ? 'attention' : 'default'} />
                  <StatCard label="Ingreso recurrente (MRR)" value={formatCOP(data.commerce.mrr)} hint="Suscripciones al día" />
                </div>
                <BarList title="Almacenes por plan" rows={data.commerce.subscriptionsByPlan} empty="Sin almacenes activos." />
              </Section>
            </div>
          )
        }}
      </QueryBoundary>
    </AdminPage>
  )
}
