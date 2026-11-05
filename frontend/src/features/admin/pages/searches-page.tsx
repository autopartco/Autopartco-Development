import { useSearchParams } from 'react-router'
import { SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { formatNumber } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { QueryBoundary } from '@/features/portal/components/query-boundary'
import { SelectField } from '@/features/portal/components/form-controls'
import { StatCard } from '@/features/portal/components/stat-card'
import { MAX_RANGE_DAYS, RANGE_PRESETS, dateLabel, fromYmd, isRangePreset, rangeLength, resolveRange, type RangePreset } from '@/features/portal/lib/analytics'
import { getAdminAnalyticsService } from '../api/admin-analytics-service'
import type { MunicipalitySearchStat, SearchQueryStat, SearchReport, UnmetQueryStat, VehicleSearchStat } from '../api/analytics-types'
import { AuroraHeader } from '../components/aurora-header'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const percent = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 1 })
const rate = (unmet: number, searches: number) => (searches > 0 ? percent.format(unmet / searches) : '—')

const QUERY_COLUMNS: DataColumn<SearchQueryStat>[] = [
  { id: 'consulta', header: 'Consulta', rowHeader: true, sortValue: (row) => row.query, cell: (row) => <span className="font-semibold">{row.query}</span> },
  { id: 'busquedas', header: 'Búsquedas', align: 'end', sortValue: (row) => row.searches, cell: (row) => formatNumber(row.searches) },
  { id: 'sin', header: 'Sin resultados', align: 'end', sortValue: (row) => row.unmet, cell: (row) => `${formatNumber(row.unmet)} (${rate(row.unmet, row.searches)})` },
]

const UNMET_COLUMNS: DataColumn<UnmetQueryStat>[] = [
  { id: 'consulta', header: 'Consulta', rowHeader: true, sortValue: (row) => row.query, cell: (row) => <span className="font-semibold">{row.query}</span> },
  { id: 'busquedas', header: 'Veces buscada sin resultados', align: 'end', sortValue: (row) => row.searches, cell: (row) => formatNumber(row.searches) },
]

function vehicleName(row: VehicleSearchStat): string {
  return [row.makeName, row.modelName, row.year].filter(Boolean).join(' ')
}

const VEHICLE_COLUMNS: DataColumn<VehicleSearchStat>[] = [
  { id: 'vehiculo', header: 'Vehículo', rowHeader: true, sortValue: vehicleName, cell: (row) => <span className="font-semibold">{vehicleName(row)}</span> },
  { id: 'busquedas', header: 'Búsquedas', align: 'end', sortValue: (row) => row.searches, cell: (row) => formatNumber(row.searches) },
  { id: 'sin', header: 'Sin resultados', align: 'end', sortValue: (row) => row.unmet, cell: (row) => `${formatNumber(row.unmet)} (${rate(row.unmet, row.searches)})` },
]

const MUNICIPALITY_COLUMNS: DataColumn<MunicipalitySearchStat>[] = [
  {
    id: 'municipio',
    header: 'Municipio',
    rowHeader: true,
    sortValue: (row) => row.municipalityName,
    cell: (row) => (
      <div>
        <p className="font-semibold">{row.municipalityName}</p>
        <p className="text-xs text-muted-foreground">Código DIVIPOLA {row.municipalityCode}</p>
      </div>
    ),
  },
  { id: 'busquedas', header: 'Búsquedas', align: 'end', sortValue: (row) => row.searches, cell: (row) => formatNumber(row.searches) },
  { id: 'sin', header: 'Sin resultados', align: 'end', sortValue: (row) => row.unmet, cell: (row) => `${formatNumber(row.unmet)} (${rate(row.unmet, row.searches)})` },
]

function SearchesSkeleton() {
  return (
    <div role="status">
      <span className="sr-only">Cargando las búsquedas…</span>
      <div aria-hidden className="grid gap-4 sm:grid-cols-3">
        {Array.from({ length: 3 }, (_, index) => (
          <Skeleton key={index} className="h-28" />
        ))}
        <Skeleton className="h-64 sm:col-span-3" />
      </div>
    </div>
  )
}

function Table<T>({ id, title, hint, caption, columns, rows, getRowId, sort }: { id: string; title: string; hint?: string; caption: string; columns: DataColumn<T>[]; rows: T[]; getRowId: (row: T) => string; sort: string }) {
  return (
    <section aria-labelledby={id} className="flex min-w-0 flex-col gap-3">
      <div>
        <h2 id={id} className="text-lg font-semibold tracking-tight">
          {title}
        </h2>
        {hint ? <p className="text-sm text-muted-foreground">{hint}</p> : null}
      </div>
      {rows.length === 0 ? (
        <p className="rounded-lg bg-concrete p-4 text-sm text-muted-foreground">Sin datos en este periodo.</p>
      ) : (
        <DataTable caption={caption} columns={columns} rows={rows} getRowId={getRowId} defaultSort={{ columnId: sort, direction: 'desc' }} />
      )}
    </section>
  )
}

function SearchesView({ report }: { report: SearchReport }) {
  if (report.totalSearches === 0) {
    return (
      <EmptyState
        icon={<SearchXIcon />}
        title="Sin búsquedas en este periodo"
        description={`No hubo búsquedas entre el ${dateLabel(report.from)} y el ${dateLabel(report.to)}. Prueba con un periodo más largo.`}
      />
    )
  }
  return (
    <div className="flex flex-col gap-8">
      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard label="Búsquedas" value={formatNumber(report.totalSearches)} hint="De productos en el marketplace" />
        <StatCard label="Sin resultados" value={formatNumber(report.unmetSearches)} hint="Búsquedas donde nadie encontró lo que quería" tone={report.unmetRate >= 0.15 ? 'attention' : 'default'} />
        <StatCard label="Tasa sin resultados" value={percent.format(report.unmetRate)} hint="Del total de búsquedas" tone={report.unmetRate >= 0.15 ? 'attention' : 'default'} />
      </div>
      <div className="grid gap-8 lg:grid-cols-2">
        <Table id="bus-consultas" title="Consultas más frecuentes" caption="Consultas más buscadas" columns={QUERY_COLUMNS} rows={report.topQueries} getRowId={(row) => row.query} sort="busquedas" />
        <Table
          id="bus-sin"
          title="Consultas sin resultados"
          hint="Lo que la gente busca y no encuentra: son oportunidades de catálogo."
          caption="Consultas sin resultados"
          columns={UNMET_COLUMNS}
          rows={report.topUnmetQueries}
          getRowId={(row) => row.query}
          sort="busquedas"
        />
        <Table
          id="bus-vehiculos"
          title="Vehículos más buscados"
          caption="Vehículos más buscados"
          columns={VEHICLE_COLUMNS}
          rows={report.topVehicles}
          getRowId={(row) => `${row.makeId}:${row.modelId ?? ''}:${row.year ?? ''}`}
          sort="busquedas"
        />
        <Table id="bus-municipios" title="Búsquedas por municipio" caption="Búsquedas por municipio" columns={MUNICIPALITY_COLUMNS} rows={report.byMunicipality} getRowId={(row) => row.municipalityCode} sort="busquedas" />
      </div>
    </div>
  )
}

/** `/admin/busquedas`: qué busca la gente en el marketplace y qué no encuentra. */
export default function AdminSearchesPage() {
  const [params, setParams] = useSearchParams()
  const preset: RangePreset = isRangePreset(params.get('rango')) ? (params.get('rango') as RangePreset) : '30'
  const customFrom = params.get('desde') ?? ''
  const customTo = params.get('hasta') ?? ''
  const range = resolveRange(preset, { from: customFrom || undefined, to: customTo || undefined })
  const days = rangeLength(range.from, range.to)
  const rangeError =
    preset !== 'personalizado'
      ? undefined
      : !fromYmd(customFrom) || !fromYmd(customTo)
        ? 'Elige la fecha inicial y la final.'
        : days < 1
          ? 'La fecha final debe ser igual o posterior a la inicial.'
          : days > MAX_RANGE_DAYS
            ? `El rango puede abarcar máximo ${MAX_RANGE_DAYS} días.`
            : undefined
  const query = rangeError ? resolveRange('30', {}) : range
  const result = useAsync(`${ADMIN_CACHE_PREFIX}analytics:searches:${query.from}:${query.to}`, async () => (await getAdminAnalyticsService()).searches(query))

  function update(values: Record<string, string>) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [name, value] of Object.entries(values)) {
          if (value) next.set(name, value)
          else next.delete(name)
        }
        return next
      },
      { replace: true },
    )
  }

  return (
    <main>
      <title>Búsquedas | Administración | Autopartco</title>
      <AuroraHeader
        label="Inteligencia comercial"
        title="Búsquedas"
        description="Qué buscan las personas en el marketplace, desde dónde y qué no encuentran, para saber qué productos y almacenes hacen falta."
      />
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-8 sm:px-6 lg:px-8">
        <form role="search" aria-label="Periodo de las búsquedas" className="flex flex-col flex-wrap gap-3 sm:flex-row sm:items-end" onSubmit={(event) => event.preventDefault()}>
          <SelectField
            id="bus-rango"
            label="Periodo"
            value={preset}
            onChange={(event) =>
              update(
                event.target.value === 'personalizado'
                  ? { rango: 'personalizado', desde: range.from, hasta: range.to }
                  : { rango: event.target.value === '30' ? '' : event.target.value, desde: '', hasta: '' },
              )
            }
            options={RANGE_PRESETS}
            containerClassName="w-full sm:w-48"
          />
          {preset === 'personalizado' ? (
            <>
              <div className="flex w-full flex-col gap-1.5 sm:w-44">
                <label htmlFor="bus-desde" className="text-sm font-semibold">
                  Desde
                </label>
                <input id="bus-desde" type="date" value={customFrom} max={customTo || undefined} onChange={(event) => update({ desde: event.target.value })} className={INPUT_CLASS} aria-invalid={rangeError ? true : undefined} />
              </div>
              <div className="flex w-full flex-col gap-1.5 sm:w-44">
                <label htmlFor="bus-hasta" className="text-sm font-semibold">
                  Hasta
                </label>
                <input id="bus-hasta" type="date" value={customTo} min={customFrom || undefined} onChange={(event) => update({ hasta: event.target.value })} className={INPUT_CLASS} aria-invalid={rangeError ? true : undefined} />
              </div>
            </>
          ) : null}
        </form>
        {rangeError ? (
          <p role="alert" className="rounded-lg border border-destructive/25 bg-destructive/5 p-4 text-sm font-medium text-destructive">
            {rangeError}
          </p>
        ) : (
          <QueryBoundary result={result} what="las búsquedas" keepPrevious skeleton={<SearchesSkeleton />}>
            {(report) => <SearchesView report={report} />}
          </QueryBoundary>
        )}
      </div>
    </main>
  )
}
