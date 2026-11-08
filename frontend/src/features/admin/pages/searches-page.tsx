import { useSearchParams } from 'react-router'
import { SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { formatNumber, pluralize } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { ChartEmpty, ChartSection, ChartSkeleton } from '@/features/portal/components/chart-section'
import { SelectField } from '@/features/portal/components/form-controls'
import { CHART_BG } from '@/features/portal/lib/chart-palette'
import { MAX_RANGE_DAYS, RANGE_PRESETS, dateLabel, fromYmd, isRangePreset, rangeLength, resolveRange, type RangePreset } from '@/features/portal/lib/analytics'
import { getAdminAnalyticsService } from '../api/admin-analytics-service'
import type { MunicipalitySearchStat, SearchQueryStat, SearchReport, UnmetQueryStat, VehicleSearchStat } from '../api/analytics-types'
import { AdminBoundary, AdminPage, AdminSection, FigureList } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const percent = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 1 })
const rate = (unmet: number, searches: number) => (searches > 0 ? percent.format(unmet / searches) : '—')
/** Umbral a partir del cual la tasa sin resultados pide revisar el catálogo. */
const UNMET_ATTENTION = 0.15

const unmetCell = (row: { unmet: number; searches: number }) => (
  <span className="ap-num">
    {formatNumber(row.unmet)} <span className="text-ap-muted">({rate(row.unmet, row.searches)})</span>
  </span>
)

const QUERY_COLUMNS: DataColumn<SearchQueryStat>[] = [
  { id: 'consulta', header: 'Consulta', rowHeader: true, sortValue: (row) => row.query, cell: (row) => <span className="ap-ref font-semibold text-ap-ink">{row.query}</span> },
  { id: 'busquedas', header: 'Búsquedas', align: 'end', sortValue: (row) => row.searches, cell: (row) => <span className="ap-num">{formatNumber(row.searches)}</span> },
  { id: 'sin', header: 'Sin resultados', align: 'end', sortValue: (row) => row.unmet, cell: unmetCell },
]

function vehicleName(row: VehicleSearchStat): string {
  return [row.makeName, row.modelName, row.year].filter(Boolean).join(' ')
}

const VEHICLE_COLUMNS: DataColumn<VehicleSearchStat>[] = [
  { id: 'vehiculo', header: 'Vehículo', rowHeader: true, sortValue: vehicleName, cell: (row) => <span className="font-semibold text-ap-ink">{vehicleName(row)}</span> },
  { id: 'busquedas', header: 'Búsquedas', align: 'end', sortValue: (row) => row.searches, cell: (row) => <span className="ap-num">{formatNumber(row.searches)}</span> },
  { id: 'sin', header: 'Sin resultados', align: 'end', sortValue: (row) => row.unmet, cell: unmetCell },
]

const MUNICIPALITY_COLUMNS: DataColumn<MunicipalitySearchStat>[] = [
  {
    id: 'municipio',
    header: 'Municipio',
    rowHeader: true,
    sortValue: (row) => row.municipalityName,
    cell: (row) => (
      <div>
        <p className="font-semibold text-ap-ink">{row.municipalityName}</p>
        <p className="ap-ref text-ap-muted">DIVIPOLA {row.municipalityCode}</p>
      </div>
    ),
  },
  { id: 'busquedas', header: 'Búsquedas', align: 'end', sortValue: (row) => row.searches, cell: (row) => <span className="ap-num">{formatNumber(row.searches)}</span> },
  { id: 'sin', header: 'Sin resultados', align: 'end', sortValue: (row) => row.unmet, cell: unmetCell },
]

function SearchesSkeleton() {
  return (
    <div className="flex flex-col gap-8">
      <ChartSkeleton kind="list" label="Cargando las búsquedas sin resultados" />
      <div className="grid gap-8 lg:grid-cols-2">
        <ChartSkeleton kind="list" label="Cargando las consultas" />
        <ChartSkeleton kind="list" label="Cargando los vehículos" />
      </div>
    </div>
  )
}

/**
 * «¿Qué buscan y no encuentran?»: barras horizontales ordenadas (§20.4 B) en rojo, la serie principal de la
 * pantalla. Cada fila lleva la consulta en mono y la cifra en texto; la lista es la alternativa accesible.
 */
function UnmetBars({ rows }: { rows: UnmetQueryStat[] }) {
  const sorted = [...rows].sort((a, b) => b.searches - a.searches)
  const max = Math.max(1, ...sorted.map((row) => row.searches))
  return (
    <ol className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
      {sorted.map((row, index) => (
        <li key={row.query} className="grid grid-cols-[2rem_minmax(0,1fr)_4.5rem] items-center gap-x-3 py-2 sm:grid-cols-[2rem_minmax(0,16rem)_minmax(0,1fr)_4.5rem]">
          <span className="ap-ref text-right text-ap-muted">{index + 1}</span>
          <span className="ap-ref truncate font-semibold text-ap-ink" title={row.query}>
            {row.query}
          </span>
          <span aria-hidden className={cn('col-start-2 row-start-2 mt-1 h-2 sm:col-start-auto sm:row-start-auto sm:mt-0', CHART_BG.track)}>
            <span className={cn('block h-full', CHART_BG.primary)} style={{ width: `${Math.max(1, (row.searches / max) * 100)}%` }} />
          </span>
          <span className="ap-num text-sm font-semibold text-ap-ink">
            {formatNumber(row.searches)}
            <span className="sr-only"> búsquedas sin resultados</span>
          </span>
        </li>
      ))}
    </ol>
  )
}

function Table<T>({ id, title, meta, caption, columns, rows, getRowId, sort }: { id: string; title: string; meta: string; caption: string; columns: DataColumn<T>[]; rows: T[]; getRowId: (row: T) => string; sort: string }) {
  return (
    <AdminSection id={id} title={title} meta={meta}>
      {rows.length === 0 ? (
        <ChartEmpty>No hubo datos de este tipo en el periodo elegido. Prueba con un periodo más largo.</ChartEmpty>
      ) : (
        <DataTable caption={caption} columns={columns} rows={rows} getRowId={getRowId} defaultSort={{ columnId: sort, direction: 'desc' }} />
      )}
    </AdminSection>
  )
}

/**
 * Búsquedas (§20.6). Pregunta principal: «¿qué falta en el catálogo?» → consultas sin resultados primero, con
 * el volumen total como contexto; después, las tablas de consultas, vehículos y municipios con valores exactos.
 */
function SearchesView({ report }: { report: SearchReport }) {
  const period = `${dateLabel(report.from)} – ${dateLabel(report.to)}`
  if (report.totalSearches === 0) {
    return (
      <EmptyState
        icon={<SearchXIcon />}
        title="Sin búsquedas en este periodo"
        description={`No hubo búsquedas entre el ${dateLabel(report.from)} y el ${dateLabel(report.to)}. Prueba con un periodo más largo.`}
      />
    )
  }
  const attention = report.unmetRate >= UNMET_ATTENTION
  return (
    <div className="flex flex-col gap-8">
      <ChartSection
        title="¿Qué buscan las personas y no encuentran?"
        meta={`${period} · búsquedas sin resultados por consulta`}
        lead={
          <FigureList
            items={[
              { label: 'Búsquedas', value: formatNumber(report.totalSearches), hint: 'De productos en el marketplace' },
              { label: 'Sin resultados', value: formatNumber(report.unmetSearches), attention },
              {
                label: 'Tasa sin resultados',
                value: percent.format(report.unmetRate),
                attention,
                hint: attention ? `Sobre el ${percent.format(UNMET_ATTENTION)}: revisa qué falta en el catálogo` : 'Del total de búsquedas',
              },
            ]}
          />
        }
        source="Fuente: búsquedas registradas en el marketplace. Las consultas sin resultados son oportunidades de catálogo o de almacenes."
      >
        {report.topUnmetQueries.length === 0 ? (
          <ChartEmpty>Todas las búsquedas del periodo tuvieron al menos un resultado.</ChartEmpty>
        ) : (
          <UnmetBars rows={report.topUnmetQueries} />
        )}
      </ChartSection>

      <div className="grid gap-8 lg:grid-cols-2">
        <Table
          id="bus-consultas"
          title="Consultas más frecuentes"
          meta={`${period} · búsquedas`}
          caption="Consultas más buscadas"
          columns={QUERY_COLUMNS}
          rows={report.topQueries}
          getRowId={(row) => row.query}
          sort="busquedas"
        />
        <Table
          id="bus-vehiculos"
          title="Vehículos más buscados"
          meta={`${period} · búsquedas`}
          caption="Vehículos más buscados"
          columns={VEHICLE_COLUMNS}
          rows={report.topVehicles}
          getRowId={(row) => `${row.makeId}:${row.modelId ?? ''}:${row.year ?? ''}`}
          sort="busquedas"
        />
      </div>
      <Table
        id="bus-municipios"
        title="Búsquedas por municipio"
        meta={`${period} · ${pluralize(report.byMunicipality.length, 'municipio', 'municipios')} · búsquedas`}
        caption="Búsquedas por municipio"
        columns={MUNICIPALITY_COLUMNS}
        rows={report.byMunicipality}
        getRowId={(row) => row.municipalityCode}
        sort="busquedas"
      />
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
    <AdminPage title="Búsquedas" description="Qué buscan las personas en el marketplace, desde dónde y qué no encuentran, para saber qué productos y almacenes hacen falta.">
      <div className="flex flex-col gap-6">
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
                <label htmlFor="bus-desde" className="text-sm font-semibold text-ap-ink">
                  Desde
                </label>
                <input id="bus-desde" type="date" value={customFrom} max={customTo || undefined} onChange={(event) => update({ desde: event.target.value })} className={INPUT_CLASS} aria-invalid={rangeError ? true : undefined} aria-describedby={rangeError ? 'bus-rango-error' : undefined} />
              </div>
              <div className="flex w-full flex-col gap-1.5 sm:w-44">
                <label htmlFor="bus-hasta" className="text-sm font-semibold text-ap-ink">
                  Hasta
                </label>
                <input id="bus-hasta" type="date" value={customTo} min={customFrom || undefined} onChange={(event) => update({ hasta: event.target.value })} className={INPUT_CLASS} aria-invalid={rangeError ? true : undefined} aria-describedby={rangeError ? 'bus-rango-error' : undefined} />
              </div>
            </>
          ) : null}
        </form>
        {rangeError ? (
          <p id="bus-rango-error" role="alert" className="rounded-md border border-ap-danger/30 bg-ap-danger-soft p-4 text-sm font-medium text-ap-danger">
            {rangeError}
          </p>
        ) : (
          <AdminBoundary result={result} what="las búsquedas" keepPrevious skeleton={<SearchesSkeleton />}>
            {(report) => <SearchesView report={report} />}
          </AdminBoundary>
        )}
      </div>
    </AdminPage>
  )
}
