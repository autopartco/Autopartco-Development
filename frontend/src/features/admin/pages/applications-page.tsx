import { useMemo, useState } from 'react'
import { ClipboardCheckIcon, SearchIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { QueryError } from '@/features/portal/components/query-boundary'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { formatDate } from '@/features/portal/lib/labels'
import type { StoreApplication } from '../api/types'
import { ApplicationSheet } from '../components/application-sheet'
import { AuroraHeader } from '../components/aurora-header'
import { TimelineScrubber } from '../components/timeline-scrubber'
import { usePendingApplications } from '../hooks'
import { LEGAL_TYPE_LABEL, timeOf } from '../lib/labels'
import { dayOffset, spanDays, type DayRange } from '../lib/timeline'

type LegalFilter = 'ALL' | StoreApplication['legalType']

/** Normaliza para buscar sin tildes ni mayúsculas ("medellin" encuentra "Medellín"). */
const normalize = (value: string): string => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

export default function AdminApplicationsPage() {
  const result = usePendingApplications()
  const [query, setQuery] = useState('')
  const [legal, setLegal] = useState<LegalFilter>('ALL')
  const [range, setRange] = useState<DayRange>({ from: 0, to: Number.POSITIVE_INFINITY })
  const [selectedId, setSelectedId] = useState<string>()

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
  const filtersActive = query.trim() !== '' || legal !== 'ALL' || from > 0 || to < days

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
          <p className="font-semibold">{row.businessName}</p>
          <p className="text-sm text-muted-foreground">NIT {row.nit}</p>
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
    { id: 'applicant', header: 'Solicitante', cell: (row) => row.applicantEmail, className: 'hidden lg:table-cell' },
    {
      id: 'docs',
      header: 'Documentos',
      sortValue: (row) => row.documents.length,
      cell: (row) => row.documents.length,
      className: 'hidden sm:table-cell',
    },
    {
      id: 'date',
      header: 'Enviada',
      sortValue: (row) => timeOf(row.createdAt),
      cell: (row) => formatDate(row.createdAt),
    },
    { id: 'status', header: 'Estado', cell: () => <StatusBadge tone="warning">En revisión</StatusBadge> },
    {
      id: 'actions',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button
          variant="outline"
          className="h-11 px-4"
          aria-label={`Revisar la solicitud de ${row.businessName}`}
          onClick={() => setSelectedId(row.applicationId)}
        >
          Revisar
        </Button>
      ),
    },
  ]

  let body
  if (result.status === 'error') {
    body = <QueryError error={result.error} what="las solicitudes de almacenes" onRetry={result.retry} />
  } else if (result.data === undefined) {
    body = <ListSkeleton rows={5} label="Cargando solicitudes de almacenes" />
  } else if (all.length === 0) {
    body = (
      <EmptyState
        icon={<ClipboardCheckIcon />}
        title="No hay solicitudes por revisar"
        description="Cuando un almacén envíe su solicitud, aparecerá aquí con sus documentos."
      >
        <Button variant="outline" className="h-11 px-4" onClick={result.retry}>
          Actualizar
        </Button>
      </EmptyState>
    )
  } else {
    body = (
      <div className="flex flex-col gap-4" aria-busy={result.status === 'loading' || undefined}>
        <form role="search" aria-label="Filtrar solicitudes" className="flex flex-col gap-3 sm:flex-row" onSubmit={(event) => event.preventDefault()}>
          <div className="relative flex-1">
            <label htmlFor="admin-search" className="sr-only">
              Buscar por almacén, NIT o correo
            </label>
            <SearchIcon aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <input
              id="admin-search"
              type="search"
              className={`${INPUT_CLASS} pl-9`}
              placeholder="Buscar por almacén, NIT o correo"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>
          <div className="sm:w-56">
            <label htmlFor="admin-legal" className="sr-only">
              Tipo de solicitante
            </label>
            <select id="admin-legal" className={`${INPUT_CLASS} pr-8`} value={legal} onChange={(event) => setLegal(event.target.value as LegalFilter)}>
              <option value="ALL">Todos los tipos</option>
              <option value="NATURAL_PERSON">Persona natural</option>
              <option value="LEGAL_ENTITY">Persona jurídica</option>
            </select>
          </div>
        </form>
        <p className="text-sm text-muted-foreground" role="status">
          {filtered.length} {filtered.length === 1 ? 'solicitud' : 'solicitudes'}
          {filtersActive ? ` de ${all.length}` : ''}
        </p>
        {filtered.length === 0 ? (
          <EmptyState icon={<SearchXIcon />} title="Ninguna solicitud coincide" description="Prueba con otra búsqueda o amplía el rango de fechas.">
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
    <main>
      <AuroraHeader
        label="Verificación de almacenes"
        title="Solicitudes de almacenes"
        description="Revisa los documentos de cada solicitud y decide si el almacén puede empezar a vender en Autopartco."
      >
        {all.length > 0 ? (
          <TimelineScrubber applications={all} range={{ from, to }} onRangeChange={setRange} selectedId={selectedId} onSelect={setSelectedId} />
        ) : null}
      </AuroraHeader>
      <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">{body}</div>
      <ApplicationSheet application={selected} onClose={() => setSelectedId(undefined)} />
    </main>
  )
}
