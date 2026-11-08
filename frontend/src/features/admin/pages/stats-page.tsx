import { useSearchParams } from 'react-router'
import { formatCOP, formatNumber, pluralize } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { AnalyticsChart, type ChartPoint } from '@/features/portal/components/analytics-chart'
import { ChartEmpty, ChartSection, ChartSkeleton } from '@/features/portal/components/chart-section'
import { SelectField } from '@/features/portal/components/form-controls'
import { SEGMENT_GROUP_CLASS, segmentClass } from '@/features/portal/components/segmented'
import { CHART_BG } from '@/features/portal/lib/chart-palette'
import { GRANULARITY_OPTIONS, RANGE_PRESETS, dateLabel, isGranularity, isRangePreset, periodLabel, resolveRange, type RangePreset } from '@/features/portal/lib/analytics'
import { getAdminInsightsService } from '../api/admin-insights-service'
import type { PlatformStatsReport, StatCount, StatsGranularity } from '../api/insights-types'
import { AdminBoundary, AdminPage, FigureList } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const PRESETS = RANGE_PRESETS.filter((preset) => preset.value !== 'personalizado')
const percent = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 1 })
const TYPE_LABEL: Record<string, string> = { CUSTOMER: 'Clientes', STORE_MANAGER: 'Almacenes', ROOT: 'Administración', ADMIN: 'Administración' }
const PROVIDER_LABEL: Record<string, string> = { google: 'Google', facebook: 'Facebook' }
const GRANULARITY_NOUN: Record<StatsGranularity, string> = { DAY: 'por día', WEEK: 'por semana', MONTH: 'por mes' }
/** Fracción de ingresos fallidos a partir de la cual conviene revisar la auditoría. */
const FAILED_ATTENTION = 0.2

/** Encabezado de un grupo de preguntas (Tráfico, Cuentas…): índice mono y título de bloque. */
function Group({ id, index, title, description, children }: { id: string; index: string; title: string; description?: string; children: React.ReactNode }) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <p className="ap-index">{index}</p>
        <h2 id={id} className="text-block-title text-ap-ink">
          {title}
        </h2>
        {description ? <p className="max-w-prose text-sm text-ap-muted">{description}</p> : null}
      </div>
      {children}
    </section>
  )
}

/**
 * Barras horizontales ordenadas (§20.4 B) para categorías sin orden temporal (rutas, referentes, tipos,
 * planes): en grafito, con la cifra y la participación en texto. La lista es su propia alternativa accesible.
 */
function BarList({ rows, format = formatNumber, label = (key: string) => key, mono = false }: { rows: StatCount[]; format?: (value: number) => string; label?: (key: string) => string; mono?: boolean }) {
  const sorted = [...rows].sort((a, b) => b.count - a.count)
  const max = Math.max(1, ...sorted.map((row) => row.count))
  const total = sorted.reduce((sum, row) => sum + row.count, 0)
  return (
    <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
      {sorted.map((row) => (
        <li key={row.key} className="flex flex-col gap-1.5 py-2.5">
          <div className="flex items-baseline justify-between gap-3 text-sm">
            <span className={cn('min-w-0 truncate text-ap-ink', mono ? 'ap-ref' : 'font-medium')} title={label(row.key)}>
              {label(row.key)}
            </span>
            <span className="shrink-0 tabular-nums">
              <span className="font-semibold text-ap-ink">{format(row.count)}</span>
              {total > 0 ? <span className="ml-1.5 text-xs text-ap-muted">{percent.format(row.count / total)}</span> : null}
            </span>
          </div>
          <div aria-hidden className={cn('h-1.5', CHART_BG.track)}>
            <div className={cn('h-full', CHART_BG.secondary)} style={{ width: `${Math.max(1, (row.count / max) * 100)}%` }} />
          </div>
        </li>
      ))}
    </ul>
  )
}

function StatsSkeleton() {
  return (
    <div className="flex flex-col gap-8">
      <ChartSkeleton kind="bars" label="Cargando el tráfico" />
      <div className="grid gap-8 lg:grid-cols-2">
        <ChartSkeleton kind="list" label="Cargando las páginas más vistas" />
        <ChartSkeleton kind="list" label="Cargando los referentes" />
      </div>
    </div>
  )
}

function GranularityControl({ value, onChange }: { value: StatsGranularity; onChange: (value: StatsGranularity) => void }) {
  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className="mb-1.5 text-sm font-semibold text-ap-ink">Agrupar</legend>
      <div className={SEGMENT_GROUP_CLASS}>
        {GRANULARITY_OPTIONS.map((option) => (
          <label
            key={option.value}
            className={segmentClass(value === option.value, 'relative has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ap-red')}
          >
            <input type="radio" name="agrupar" value={option.value} checked={value === option.value} onChange={() => onChange(option.value as StatsGranularity)} className="sr-only" />
            {option.label}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

function StatsView({ data, granularity }: { data: PlatformStatsReport; granularity: StatsGranularity }) {
  const period = `${dateLabel(data.from)} – ${dateLabel(data.to)}`
  const unit = GRANULARITY_NOUN[granularity]
  const points = <T extends { date: string }>(series: T[], pick: (row: T) => number, noun: string): ChartPoint[] =>
    series.map((row) => {
      const label = periodLabel(row.date, granularity)
      return { label, detail: `${label}: ${formatNumber(pick(row))} ${noun}`, bar: pick(row) }
    })
  const axis = (value: number) => formatNumber(Math.round(value))

  const devicesTotal = Object.values(data.traffic.devices).reduce((sum, value) => sum + value, 0)
  const mobileShare = devicesTotal > 0 ? (data.traffic.devices.mobile ?? 0) / devicesTotal : 0
  const attempts = data.access.logins + data.access.failedLogins
  const failedShare = attempts > 0 ? data.access.failedLogins / attempts : 0

  const visits = points(data.traffic.series, (row) => row.visits, 'visitas')
  const accounts = points(data.accounts.series, (row) => row.created, 'cuentas nuevas')
  const logins = points(data.access.series, (row) => row.logins, 'ingresos correctos')
  const hasVisits = visits.some((point) => (point.bar ?? 0) > 0)
  const hasAccounts = accounts.some((point) => (point.bar ?? 0) > 0)
  const hasLogins = logins.some((point) => (point.bar ?? 0) > 0)

  return (
    <div className="flex flex-col gap-12">
      <Group id="g-trafico" index="Estadísticas / 01" title="Tráfico del marketplace" description="Visitas al marketplace (sin consola ni portal). Medición propia, sin datos personales.">
        <ChartSection
          title="¿Cuántas visitas recibe el marketplace?"
          meta={`${period} · visitas ${unit}`}
          lead={
            <FigureList
              items={[
                { label: 'Visitas', value: formatNumber(data.traffic.visits) },
                { label: 'Visitantes únicos', value: formatNumber(data.traffic.uniqueVisitors), hint: 'Distintos por día' },
                {
                  label: 'Páginas por visitante',
                  value: data.traffic.uniqueVisitors > 0 ? (data.traffic.visits / data.traffic.uniqueVisitors).toLocaleString('es-CO', { maximumFractionDigits: 1 }) : '—',
                },
                { label: 'Desde celular', value: devicesTotal > 0 ? percent.format(mobileShare) : '—' },
              ]}
            />
          }
          source="Fuente: medición de visitas propia de Autopartco."
        >
          {hasVisits ? (
            <AnalyticsChart mode="bars" points={visits} label={`Visitas ${unit}`} formatAxis={axis} tableHeaders={['Periodo', 'Visitas']} />
          ) : (
            <ChartEmpty>No se registraron visitas en este periodo. Prueba con un periodo más largo.</ChartEmpty>
          )}
        </ChartSection>
        <div className="grid gap-8 lg:grid-cols-2">
          <ChartSection title="¿Qué páginas se ven más?" meta={`${period} · visitas por ruta`}>
            {data.traffic.topPaths.length === 0 ? <ChartEmpty>Aún no hay visitas registradas en este periodo.</ChartEmpty> : <BarList rows={data.traffic.topPaths} mono />}
          </ChartSection>
          <ChartSection title="¿De dónde llegan las visitas?" meta={`${period} · visitas por sitio de origen`}>
            {data.traffic.referrers.length === 0 ? <ChartEmpty>Sin referentes externos en este periodo: las visitas llegaron directo o desde buscadores sin referente.</ChartEmpty> : <BarList rows={data.traffic.referrers} mono />}
          </ChartSection>
        </div>
      </Group>

      <Group id="g-cuentas" index="Estadísticas / 02" title="Cuentas">
        <ChartSection
          title="¿Cuántas cuentas se crean?"
          meta={`${period} · cuentas nuevas ${unit}`}
          lead={
            <FigureList
              items={[
                { label: 'Nuevas en el periodo', value: formatNumber(data.accounts.created) },
                { label: 'Cuentas totales', value: formatNumber(data.accounts.total) },
                { label: 'Con correo verificado', value: percent.format(data.accounts.verifiedRate) },
                {
                  label: 'Bloqueadas',
                  value: formatNumber(data.accounts.locked),
                  attention: data.accounts.locked > 0,
                  hint: data.accounts.locked > 0 ? 'Ver en auditoría' : undefined,
                  to: data.accounts.locked > 0 ? '/admin/auditoria?accion=ACCOUNT_LOCKED' : undefined,
                },
              ]}
            />
          }
        >
          {hasAccounts ? (
            <AnalyticsChart mode="bars" points={accounts} label={`Cuentas nuevas ${unit}`} formatAxis={axis} tableHeaders={['Periodo', 'Cuentas nuevas']} />
          ) : (
            <ChartEmpty>No se crearon cuentas en este periodo.</ChartEmpty>
          )}
        </ChartSection>
        <div className="grid gap-8 lg:grid-cols-2">
          <ChartSection title="¿Qué tipo de cuentas hay?" meta="Total de cuentas por tipo">
            {data.accounts.byType.length === 0 ? <ChartEmpty>Aún no hay cuentas.</ChartEmpty> : <BarList rows={data.accounts.byType} label={(key) => TYPE_LABEL[key] ?? key} />}
          </ChartSection>
          <ChartSection title="¿Cuántas están vinculadas a redes?" meta="Cuentas por proveedor de ingreso">
            {data.accounts.socialShare.length === 0 ? (
              <ChartEmpty>Nadie ha ingresado con Google o Facebook todavía.</ChartEmpty>
            ) : (
              <BarList rows={data.accounts.socialShare} label={(key) => PROVIDER_LABEL[key] ?? key} />
            )}
          </ChartSection>
        </div>
      </Group>

      <Group id="g-accesos" index="Estadísticas / 03" title="Accesos" description="Usuarios activos: cuentas con al menos un ingreso correcto hasta el final del periodo.">
        <ChartSection
          title="¿Cuántas personas ingresan?"
          meta={`${period} · ingresos correctos ${unit}`}
          lead={
            <FigureList
              items={[
                { label: 'Activos hoy', value: formatNumber(data.access.dau), hint: 'DAU' },
                { label: 'Activos 7 días', value: formatNumber(data.access.wau), hint: 'WAU' },
                { label: 'Activos 30 días', value: formatNumber(data.access.mau), hint: 'MAU' },
                { label: 'Ingresos correctos', value: formatNumber(data.access.logins) },
                {
                  label: 'Ingresos fallidos',
                  value: formatNumber(data.access.failedLogins),
                  attention: failedShare > FAILED_ATTENTION,
                  hint: attempts > 0 ? `${percent.format(failedShare)} de los intentos · ver en auditoría` : undefined,
                  to: data.access.failedLogins > 0 ? '/admin/auditoria?accion=LOGIN_FAILED' : undefined,
                },
              ]}
            />
          }
          source={failedShare > FAILED_ATTENTION ? `Más del ${percent.format(FAILED_ATTENTION)} de los intentos fallaron: revisa en auditoría si se concentran en pocas cuentas.` : undefined}
        >
          {hasLogins ? (
            <AnalyticsChart mode="bars" points={logins} label={`Ingresos correctos ${unit}`} formatAxis={axis} tableHeaders={['Periodo', 'Ingresos']} />
          ) : (
            <ChartEmpty>No hubo ingresos correctos en este periodo.</ChartEmpty>
          )}
        </ChartSection>
      </Group>

      <Group id="g-comercio" index="Estadísticas / 04" title="Comercio" description={`${period}. Valores en pesos colombianos (COP).`}>
        <FigureList
          className="border-t border-ap-line-strong pt-4"
          items={[
            { label: 'Pedidos válidos', value: formatNumber(data.commerce.orders) },
            { label: 'Ventas (GMV)', value: formatCOP(data.commerce.gmv) },
            { label: 'Ticket promedio', value: formatCOP(data.commerce.averageTicket) },
            { label: 'Ingreso recurrente (MRR)', value: formatCOP(data.commerce.mrr), hint: 'Suscripciones al día' },
            {
              label: 'Solicitudes por revisar',
              value: formatNumber(data.commerce.pendingApplications),
              attention: data.commerce.pendingApplications > 0,
              hint: data.commerce.pendingApplications > 0 ? 'Ir a la cola' : undefined,
              to: data.commerce.pendingApplications > 0 ? '/admin/solicitudes' : undefined,
            },
          ]}
        />
        <ChartSection
          title="¿En qué plan están los almacenes?"
          meta="Almacenes activos por plan"
          source={`${pluralize(data.commerce.activeStores, 'almacén activo', 'almacenes activos')} en total.`}
        >
          {data.commerce.subscriptionsByPlan.length === 0 ? <ChartEmpty>Aún no hay almacenes activos con plan.</ChartEmpty> : <BarList rows={data.commerce.subscriptionsByPlan} />}
        </ChartSection>
      </Group>
    </div>
  )
}

/** `/admin/estadisticas`: tráfico, cuentas, accesos y comercio de toda la plataforma, organizados por pregunta (§20.6). */
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

  return (
    <AdminPage title="Estadísticas" description="Tráfico del marketplace, cuentas, accesos y resultados comerciales de toda la plataforma.">
      <form aria-label="Periodo y agrupación" className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end" onSubmit={(event) => event.preventDefault()}>
        <SelectField id="stats-rango" label="Periodo" options={PRESETS} value={preset} onChange={(event) => update('rango', event.target.value)} containerClassName="w-full sm:w-56" />
        <GranularityControl value={granularity} onChange={(value) => update('agrupar', value)} />
      </form>
      <AdminBoundary result={report} what="las estadísticas" keepPrevious skeleton={<StatsSkeleton />}>
        {(data) => <StatsView data={data} granularity={granularity} />}
      </AdminBoundary>
    </AdminPage>
  )
}
