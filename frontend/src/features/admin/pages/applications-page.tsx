import { useMemo, useState } from 'react'
import { useLocation } from 'react-router'
import { ClipboardCheckIcon, RotateCwIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { pluralize } from '@/lib/format'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { SearchField } from '@/features/portal/components/search-field'
import { SEGMENT_GROUP_CLASS, segmentClass } from '@/features/portal/components/segmented'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { formatDate } from '@/features/portal/lib/labels'
import type { StoreApplication } from '../api/types'
import { AdminPage, AdminQueryError } from '../components/admin-kit'
import { ApplicationSheet } from '../components/application-sheet'
import { TimelineScrubber } from '../components/timeline-scrubber'
import { usePendingApplications } from '../hooks'
import { DAY_MS, LEGAL_TYPE_LABEL, startOfDay, timeOf } from '../lib/labels'
import { dayOffset, spanDays, type DayRange } from '../lib/timeline'

type LegalFilter = 'ALL' | StoreApplication['legalType']

const LEGAL_OPTIONS: { value: LegalFilter; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'NATURAL_PERSON', label: 'Persona natural' },
  { value: 'LEGAL_ENTITY', label: 'Persona jurídica' },
]

/** Normaliza para buscar sin tildes ni mayúsculas ("medellin" encuentra "Medellín"). */
const normalize = (value: string): string => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

/** Días completos desde el envío (0 = hoy). */
const daysWaiting = (createdAt: string) => Math.max(0, Math.round((startOfDay(Date.now()) - startOfDay(timeOf(createdAt))) / DAY_MS))

/**
 * `/admin/solicitudes`: cola de verificación de almacenes (§7.4). Lectura en la tabla, filtros arriba
 * (texto, tipo y fecha de envío) y la decisión solo dentro del panel de cada solicitud.
 * El servidor devuelve solo las pendientes, así que el estado de toda la cola es «En revisión».
 */
export default function AdminApplicationsPage() {
  const result = usePendingApplications()
  const location = useLocation()
  const [query, setQuery] = useState('')
  const [legal, setLegal] = useState<LegalFilter>('ALL')
  const [range, setRange] = useState<DayRange>({ from: 0, to: Number.POSITIVE_INFINITY })
  // Desde el resumen se llega con la solicitud a abrir en `state.open`.
  const [selectedId, setSelectedId] = useState<string | undefined>(() => {
    const state = location.state as { open?: unknown } | null
    return typeof state?.open === 'string' ? state.open : undefined
  })

  const all = useMemo(() => result.data ?? [], [result.data])
  const { start, days } = spanDays(all)
  const from = Math.min(range.from, days)
  const to = Math.min(Math.max(range.to, from), days)
  const filtered = useMemo(() => {
    const needle = normalize(query.trim())
    return all.filter((item) => {
      if (legal !== 'ALL' && item.legalType !== legal) return false
      const offset = dayOffset(item.createdAt, start)
      if (offset < from || offset > to) return false
      if (!needle) return true
      return [item.businessName, item.nit, item.businessEmail, item.applicantEmail].some((field) => normalize(field).includes(needle))
    })
  }, [all, legal, query, start, from, to])

  const selected = all.find((item) => item.applicationId === selectedId)
  const oldest = [...all].sort((a, b) => timeOf(a.createdAt) - timeOf(b.createdAt))[0]
  const activeFilters = [query.trim() !== '', legal !== 'ALL', from > 0 || to < days].filter(Boolean).length

  function clearFilters() {
    setQuery('')
    setLegal('ALL')
    setRange({ from: 0, to: Number.POSITIVE_INFINITY })
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
        </div>
      ),
    },
    {
      id: 'type',
      header: 'Tipo',
      sortValue: (row) => LEGAL_TYPE_LABEL[row.legalType],
      cell: (row) => LEGAL_TYPE_LABEL[row.legalType],
      className: 'hidden md:table-cell',
    },
    { id: 'applicant', header: 'Solicitante', cell: (row) => <span className="break-all">{row.applicantEmail}</span>, className: 'hidden lg:table-cell' },
    {
      id: 'docs',
      header: 'Documentos',
      align: 'end',
      sortValue: (row) => row.documents.length,
      cell: (row) =>
        row.documents.length === 0 ? (
          <StatusBadge tone="warning">Sin documentos</StatusBadge>
        ) : (
          <span className="ap-num">{row.documents.length}</span>
        ),
      className: 'hidden sm:table-cell',
    },
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
            <p className={waiting >= 3 ? 'text-xs font-medium text-ap-warning' : 'text-xs text-ap-muted'}>
              {waiting === 0 ? 'Hoy' : `Hace ${pluralize(waiting, 'día', 'días')}`}
            </p>
          </div>
        )
      },
    },
    { id: 'status', header: 'Estado', cell: () => <StatusBadge tone="warning">En revisión</StatusBadge> },
    {
      id: 'actions',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button variant="outline" className="h-11 px-4" aria-label={`Revisar la solicitud de ${row.businessName}`} onClick={() => setSelectedId(row.applicationId)}>
          Revisar
        </Button>
      ),
    },
  ]

  let body
  if (result.status === 'error' && result.data === undefined) {
    body = <AdminQueryError error={result.error} what="las solicitudes de almacenes" onRetry={result.retry} />
  } else if (result.data === undefined) {
    body = <ListSkeleton rows={5} label="Cargando solicitudes de almacenes" />
  } else if (all.length === 0) {
    body = (
      <EmptyState
        icon={<ClipboardCheckIcon />}
        title="No hay solicitudes por revisar"
        description="Cuando un almacén envíe su solicitud, aparecerá aquí con sus documentos para que la verifiques."
      >
        <Button variant="outline" className="h-11 px-4" onClick={result.retry}>
          <RotateCwIcon aria-hidden />
          Actualizar
        </Button>
      </EmptyState>
    )
  } else {
    body = (
      <div className="flex flex-col gap-6" aria-busy={result.status === 'loading' || undefined}>
        <form role="search" aria-label="Filtrar solicitudes" className="flex flex-col gap-4 lg:flex-row lg:items-end" onSubmit={(event) => event.preventDefault()}>
          <SearchField label="Buscar" value={query} onChange={setQuery} placeholder="Almacén, NIT o correo" className="lg:max-w-sm lg:flex-1" />
          <fieldset className="flex flex-col gap-1.5">
            <legend className="mb-1.5 text-sm font-semibold text-ap-ink">Tipo de solicitante</legend>
            <div className={SEGMENT_GROUP_CLASS}>
              {LEGAL_OPTIONS.map((option) => (
                <label
                  key={option.value}
                  className={segmentClass(legal === option.value, 'relative has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ap-red')}
                >
                  <input type="radio" name="tipo-solicitante" value={option.value} checked={legal === option.value} onChange={() => setLegal(option.value)} className="sr-only" />
                  {option.label}
                </label>
              ))}
            </div>
          </fieldset>
        </form>

        <TimelineScrubber applications={all} range={{ from, to }} onRangeChange={setRange} selectedId={selectedId} onSelect={setSelectedId} />

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-ap-line pt-4">
          <p className="text-sm text-ap-muted" role="status">
            <span className="ap-num font-semibold text-ap-ink">{filtered.length}</span> {filtered.length === 1 ? 'solicitud' : 'solicitudes'}
            {activeFilters > 0 ? ` de ${all.length} · ${pluralize(activeFilters, 'filtro activo', 'filtros activos')}` : ' en la cola'} · orden: la más antigua primero
          </p>
          {activeFilters > 0 ? (
            <Button variant="ghost" className="h-11 px-3 underline underline-offset-4" onClick={clearFilters}>
              Quitar filtros
            </Button>
          ) : null}
        </div>

        {filtered.length === 0 ? (
          <EmptyState icon={<SearchXIcon />} title="Ninguna solicitud coincide" description="Prueba con otra búsqueda, otro tipo de solicitante o amplía el rango de fechas.">
            <Button variant="outline" className="h-11 px-4" onClick={clearFilters}>
              Quitar filtros
            </Button>
          </EmptyState>
        ) : (
          <DataTable
            caption="Solicitudes de almacenes pendientes de revisión"
            columns={columns}
            rows={filtered}
            getRowId={(row) => row.applicationId}
            defaultSort={{ columnId: 'date', direction: 'asc' }}
          />
        )}
      </div>
    )
  }

  return (
    <AdminPage
      title="Solicitudes de almacenes"
      description="Revisa los documentos de cada solicitud y decide si el almacén puede empezar a vender en Autopartco."
      meta={result.data && all.length > 0 ? <StatusBadge tone="warning">{pluralize(all.length, 'en revisión', 'en revisión')}</StatusBadge> : null}
      actions={
        oldest ? (
          <Button className="h-11 px-4" onClick={() => setSelectedId(oldest.applicationId)}>
            Revisar la más antigua
          </Button>
        ) : null
      }
    >
      {body}
      <ApplicationSheet application={selected} onClose={() => setSelectedId(undefined)} />
    </AdminPage>
  )
}
