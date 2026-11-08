import { useMemo, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router'
import { ClipboardCheckIcon, RotateCwIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { pluralize } from '@/lib/format'
import type { StoreApplicationStatus } from '@/features/seller/api/types'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { SearchField } from '@/features/portal/components/search-field'
import { SEGMENT_GROUP_CLASS, segmentClass } from '@/features/portal/components/segmented'
import { ServerPager } from '@/features/portal/components/server-pager'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { formatDate, formatDateTime } from '@/features/portal/lib/labels'
import type { StoreApplication } from '../api/types'
import { AdminBoundary, AdminPage } from '../components/admin-kit'
import { ApplicationSheet } from '../components/application-sheet'
import { TimelineScrubber } from '../components/timeline-scrubber'
import { useApplicationsList, usePendingApplications } from '../hooks'
import { APPLICATION_STATUS, DAY_MS, LEGAL_TYPE_LABEL, startOfDay, timeOf } from '../lib/labels'
import { dayOffset, spanDays, type DayRange } from '../lib/timeline'

const PAGE_SIZE = 20

type StatusView = 'pendientes' | 'por-configurar' | 'activas' | 'rechazadas' | 'todas'

/** Vistas del control segmentado, en el orden del flujo de verificación. El valor va en la URL (`?estado=`). */
const STATUS_VIEWS: { value: StatusView; label: string; status?: StoreApplicationStatus; caption: string; empty: string }[] = [
  { value: 'pendientes', label: 'Pendientes', status: 'PENDING_REVIEW', caption: 'Solicitudes pendientes de revisión', empty: 'No hay solicitudes por revisar' },
  {
    value: 'por-configurar',
    label: 'Aprobadas por configurar',
    status: 'CONFIGURATION_PENDING',
    caption: 'Solicitudes aprobadas pendientes de configuración',
    empty: 'Ninguna solicitud aprobada espera configuración',
  },
  { value: 'activas', label: 'Activas', status: 'ACTIVE', caption: 'Solicitudes de almacenes activos', empty: 'Aún no hay almacenes activos' },
  { value: 'rechazadas', label: 'Rechazadas', status: 'REJECTED', caption: 'Solicitudes rechazadas', empty: 'No hay solicitudes rechazadas' },
  { value: 'todas', label: 'Todas', caption: 'Todas las solicitudes de almacenes', empty: 'Aún no hay solicitudes de almacenes' },
]

const isStatusView = (value: string | null): value is StatusView => STATUS_VIEWS.some((view) => view.value === value)

type LegalFilter = 'ALL' | StoreApplication['legalType']

const LEGAL_OPTIONS: { value: LegalFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'NATURAL_PERSON', label: 'Persona natural' },
  { value: 'LEGAL_ENTITY', label: 'Persona jurídica' },
]

const FOCUS_RING = 'relative has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ap-red'

/** Normaliza para buscar sin tildes ni mayúsculas ("medellin" encuentra "Medellín"). */
const normalize = (value: string): string => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

/** Días completos desde el envío (0 = hoy). */
const daysWaiting = (createdAt: string) => Math.max(0, Math.round((startOfDay(Date.now()) - startOfDay(timeOf(createdAt))) / DAY_MS))

const isReviewed = (row: StoreApplication) => row.status !== 'PENDING_REVIEW'

/**
 * `/admin/solicitudes`: verificación de almacenes (§7.4). El estado se elige con un control segmentado (por
 * defecto, la cola pendiente) y el servidor pagina y ordena: pendientes más antiguas primero, revisadas por
 * fecha de revisión. La búsqueda y el tipo filtran la página cargada. La decisión solo vive en el panel.
 */
export default function AdminApplicationsPage() {
  const [params, setParams] = useSearchParams()
  const rawView = params.get('estado')
  const viewValue: StatusView = isStatusView(rawView) ? rawView : 'pendientes'
  const view = STATUS_VIEWS.find((option) => option.value === viewValue) ?? STATUS_VIEWS[0]
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  const pendingView = view.status === 'PENDING_REVIEW'

  const list = useApplicationsList(view.status, page, PAGE_SIZE)
  // Cola completa: alimenta «Revisar la más antigua», el contador y la solicitud que llega desde el resumen.
  const pending = usePendingApplications()
  const location = useLocation()
  const [query, setQuery] = useState('')
  const [legal, setLegal] = useState<LegalFilter>('ALL')
  const [range, setRange] = useState<DayRange>({ from: 0, to: Number.POSITIVE_INFINITY })
  const [selectedId, setSelectedId] = useState<string | undefined>(() => {
    const state = location.state as { open?: unknown } | null
    return typeof state?.open === 'string' ? state.open : undefined
  })

  const items = useMemo(() => list.data?.items ?? [], [list.data])
  const { start, days } = spanDays(items)
  const from = Math.min(range.from, days)
  const to = Math.min(Math.max(range.to, from), days)
  const dateFiltered = pendingView && (from > 0 || to < days)
  const filtered = useMemo(() => {
    const needle = normalize(query.trim())
    return items.filter((item) => {
      if (legal !== 'ALL' && item.legalType !== legal) return false
      if (dateFiltered) {
        const offset = dayOffset(item.createdAt, start)
        if (offset < from || offset > to) return false
      }
      if (!needle) return true
      return [item.businessName, item.nit, item.businessEmail, item.applicantEmail ?? ''].some((field) => normalize(field).includes(needle))
    })
  }, [items, legal, query, dateFiltered, start, from, to])

  const selected = items.find((item) => item.applicationId === selectedId) ?? pending.data?.find((item) => item.applicationId === selectedId)
  const oldest = pending.data?.length ? [...pending.data].sort((a, b) => timeOf(a.createdAt) - timeOf(b.createdAt))[0] : undefined
  const activeFilters = [query.trim() !== '', legal !== 'ALL', dateFiltered].filter(Boolean).length

  function clearFilters() {
    setQuery('')
    setLegal('ALL')
    setRange({ from: 0, to: Number.POSITIVE_INFINITY })
  }

  function update(next: { estado?: StatusView; pagina?: number }) {
    const merged = new URLSearchParams(params)
    if (next.estado !== undefined) {
      if (next.estado === 'pendientes') merged.delete('estado')
      else merged.set('estado', next.estado)
      merged.delete('pagina')
      setRange({ from: 0, to: Number.POSITIVE_INFINITY })
    }
    if (next.pagina !== undefined) {
      if (next.pagina > 1) merged.set('pagina', String(next.pagina))
      else merged.delete('pagina')
    }
    setParams(merged, { replace: true })
  }

  const columns: DataColumn<StoreApplication>[] = [
    {
      id: 'store',
      header: 'Almacén',
      rowHeader: true,
      sortValue: (row) => row.businessName,
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-semibold text-ap-ink">{row.businessName}</p>
          <p className="ap-ref text-ap-muted">NIT {row.nit}</p>
          {pendingView ? null : <p className="text-xs text-ap-muted">{LEGAL_TYPE_LABEL[row.legalType]}</p>}
        </div>
      ),
    },
    // Fuera de "Pendientes" el tipo va bajo el NIT para dejar sitio a la revisión.
    ...(pendingView
      ? [
          {
            id: 'type',
            header: 'Tipo',
            sortValue: (row) => LEGAL_TYPE_LABEL[row.legalType],
            cell: (row) => LEGAL_TYPE_LABEL[row.legalType],
            className: 'hidden md:table-cell',
          } satisfies DataColumn<StoreApplication>,
        ]
      : []),
    {
      id: 'applicant',
      header: 'Solicitante',
      cell: (row) => (row.applicantEmail ? <span className="break-all">{row.applicantEmail}</span> : <span className="text-ap-muted">Cuenta eliminada</span>),
      className: pendingView ? 'hidden lg:table-cell' : 'hidden 2xl:table-cell',
    },
    ...(pendingView
      ? [
          {
            id: 'docs',
            header: 'Documentos',
            align: 'end',
            sortValue: (row) => row.documents.length,
            cell: (row) =>
              row.documents.length === 0 ? <StatusBadge tone="warning">Sin documentos</StatusBadge> : <span className="ap-num">{row.documents.length}</span>,
            className: 'hidden sm:table-cell',
          } satisfies DataColumn<StoreApplication>,
        ]
      : []),
    {
      id: 'date',
      header: 'Enviada',
      sortValue: (row) => timeOf(row.createdAt),
      cell: (row) => {
        const waiting = daysWaiting(row.createdAt)
        return (
          <div className="whitespace-nowrap">
            <time dateTime={row.createdAt} className="ap-ref text-ap-ink">
              {formatDate(row.createdAt)}
            </time>
            {isReviewed(row) ? null : (
              <p className={waiting >= 3 ? 'text-xs font-medium text-ap-warning' : 'text-xs text-ap-muted'}>
                {waiting === 0 ? 'Hoy' : `Hace ${pluralize(waiting, 'día', 'días')}`}
              </p>
            )}
          </div>
        )
      },
      className: pendingView ? undefined : 'hidden md:table-cell',
    },
    {
      id: 'status',
      header: 'Estado',
      cell: (row) => <StatusBadge tone={APPLICATION_STATUS[row.status].tone}>{APPLICATION_STATUS[row.status].label}</StatusBadge>,
    },
    ...(pendingView
      ? []
      : [
          {
            id: 'reviewer',
            header: 'Revisada por',
            sortValue: (row) => row.reviewer?.name ?? '',
            cell: (row) =>
              !isReviewed(row) ? (
                <span className="text-ap-muted">Sin revisar</span>
              ) : row.reviewer ? (
                <div className="min-w-0">
                  <p className="text-ap-ink">{row.reviewer.name}</p>
                  <p className="truncate text-xs text-ap-muted" title={row.reviewer.email}>
                    {row.reviewer.email}
                  </p>
                </div>
              ) : (
                <span className="text-ap-muted">Revisor no disponible</span>
              ),
            className: 'hidden lg:table-cell',
          } satisfies DataColumn<StoreApplication>,
          {
            id: 'reviewedAt',
            header: 'Fecha de revisión',
            sortValue: (row) => (row.reviewedAt ? timeOf(row.reviewedAt) : 0),
            cell: (row) =>
              !isReviewed(row) ? (
                <span className="text-ap-muted">—</span>
              ) : row.reviewedAt ? (
                <time dateTime={row.reviewedAt} className="ap-ref whitespace-nowrap text-ap-ink">
                  {formatDateTime(row.reviewedAt)}
                </time>
              ) : (
                <span className="text-ap-muted">Fecha no registrada</span>
              ),
          } satisfies DataColumn<StoreApplication>,
        ]),
    {
      id: 'actions',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) =>
        isReviewed(row) ? (
          <Button variant="outline" className="h-11 px-4" aria-label={`Ver la solicitud de ${row.businessName}`} onClick={() => setSelectedId(row.applicationId)}>
            Ver
          </Button>
        ) : (
          <Button variant="outline" className="h-11 px-4" aria-label={`Revisar la solicitud de ${row.businessName}`} onClick={() => setSelectedId(row.applicationId)}>
            Revisar
          </Button>
        ),
    },
  ]

  const pendingCount = pending.data?.length ?? 0

  return (
    <AdminPage
      title="Solicitudes de almacenes"
      description="Revisa los documentos de cada solicitud, decide si el almacén puede vender en Autopartco y consulta quién revisó las anteriores."
      meta={pendingCount > 0 ? <StatusBadge tone="warning">{pluralize(pendingCount, 'en revisión', 'en revisión')}</StatusBadge> : null}
      actions={
        oldest ? (
          <Button className="h-11 px-4" onClick={() => setSelectedId(oldest.applicationId)}>
            Revisar la más antigua
          </Button>
        ) : null
      }
    >
      <div className="flex flex-col gap-6">
        <form role="search" aria-label="Filtrar solicitudes" className="flex flex-col gap-4" onSubmit={(event) => event.preventDefault()}>
          <fieldset className="flex flex-col">
            <legend className="mb-1.5 text-sm font-semibold text-ap-ink">Estado</legend>
            <div className={SEGMENT_GROUP_CLASS}>
              {STATUS_VIEWS.map((option) => (
                <label key={option.value} className={segmentClass(viewValue === option.value, FOCUS_RING)}>
                  <input
                    type="radio"
                    name="estado-solicitud"
                    value={option.value}
                    checked={viewValue === option.value}
                    onChange={() => update({ estado: option.value })}
                    className="sr-only"
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </fieldset>
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end">
            <SearchField label="Buscar en esta página" value={query} onChange={setQuery} placeholder="Almacén, NIT o correo" className="lg:max-w-sm lg:flex-1" />
            <fieldset className="flex flex-col">
              <legend className="mb-1.5 text-sm font-semibold text-ap-ink">Tipo de solicitante</legend>
              <div className={SEGMENT_GROUP_CLASS}>
                {LEGAL_OPTIONS.map((option) => (
                  <label key={option.value} className={segmentClass(legal === option.value, FOCUS_RING)}>
                    <input type="radio" name="tipo-solicitante" value={option.value} checked={legal === option.value} onChange={() => setLegal(option.value)} className="sr-only" />
                    {option.label}
                  </label>
                ))}
              </div>
            </fieldset>
          </div>
        </form>

        <AdminBoundary result={list} what="las solicitudes de almacenes" keepPrevious skeleton={<ListSkeleton rows={5} label="Cargando solicitudes de almacenes" />}>
          {(data) =>
            data.total === 0 ? (
              <EmptyState
                icon={<ClipboardCheckIcon />}
                title={view.empty}
                description={
                  pendingView
                    ? 'Cuando un almacén envíe su solicitud, aparecerá aquí con sus documentos para que la verifiques.'
                    : view.status
                      ? 'Prueba con otro estado o revisa todas las solicitudes.'
                      : 'Cuando un almacén envíe su solicitud, aparecerá aquí.'
                }
              >
                {view.status ? (
                  <Button variant="outline" className="h-11 px-4" onClick={() => update({ estado: 'todas' })}>
                    Ver todas
                  </Button>
                ) : null}
                <Button variant="outline" className="h-11 px-4" onClick={list.retry}>
                  <RotateCwIcon aria-hidden />
                  Actualizar
                </Button>
              </EmptyState>
            ) : (
              <div className="flex flex-col gap-6">
                {pendingView ? (
                  <TimelineScrubber applications={items} range={{ from, to }} onRangeChange={setRange} selectedId={selectedId} onSelect={setSelectedId} />
                ) : null}

                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-ap-line pt-4">
                  <p className="text-sm text-ap-muted" role="status">
                    <span className="ap-num font-semibold text-ap-ink">{filtered.length}</span> {filtered.length === 1 ? 'solicitud' : 'solicitudes'}
                    {activeFilters > 0 ? ` de ${items.length} en esta página · ${pluralize(activeFilters, 'filtro activo', 'filtros activos')}` : ' en esta página'}
                    {' · '}
                    {pendingView ? 'orden: la más antigua primero' : view.status ? 'orden: revisión más reciente primero' : 'orden: pendientes primero, luego por revisión'}
                  </p>
                  {activeFilters > 0 ? (
                    <Button variant="ghost" className="h-11 px-3 underline underline-offset-4" onClick={clearFilters}>
                      Quitar filtros
                    </Button>
                  ) : null}
                </div>

                {filtered.length === 0 ? (
                  <EmptyState icon={<SearchXIcon />} title="Ninguna solicitud coincide" description="Prueba con otra búsqueda u otro tipo de solicitante, o revisa todas las solicitudes.">
                    <Button variant="outline" className="h-11 px-4" onClick={clearFilters}>
                      Quitar filtros
                    </Button>
                    {view.status ? (
                      <Button
                        variant="outline"
                        className="h-11 px-4"
                        onClick={() => {
                          clearFilters()
                          update({ estado: 'todas' })
                        }}
                      >
                        Ver todas
                      </Button>
                    ) : null}
                  </EmptyState>
                ) : (
                  <DataTable
                    key={viewValue}
                    caption={view.caption}
                    columns={columns}
                    rows={filtered}
                    getRowId={(row) => row.applicationId}
                    defaultSort={pendingView ? { columnId: 'date', direction: 'asc' } : undefined}
                  />
                )}

                {data.totalPages > 1 ? (
                  <ServerPager
                    page={data.page}
                    totalPages={data.totalPages}
                    total={data.total}
                    singular="solicitud"
                    plural="solicitudes"
                    busy={list.status === 'loading'}
                    onPageChange={(next) => update({ pagina: next })}
                  />
                ) : null}
              </div>
            )
          }
        </AdminBoundary>
      </div>
      <ApplicationSheet application={selected} onClose={() => setSelectedId(undefined)} />
    </AdminPage>
  )
}
