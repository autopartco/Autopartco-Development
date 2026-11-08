import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { ScrollTextIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { pluralize } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { SelectField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { SearchField } from '@/features/portal/components/search-field'
import { ServerPager } from '@/features/portal/components/server-pager'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { RANGE_PRESETS, isRangePreset, resolveRange, type RangePreset } from '@/features/portal/lib/analytics'
import { formatDateTime } from '@/features/portal/lib/labels'
import { getAdminInsightsService } from '../api/admin-insights-service'
import type { AuditEntry } from '../api/insights-types'
import { AdminBoundary, AdminPage } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'
import { AUDIT_WARNING, auditActionLabel, auditEntityLabel } from '../lib/audit-labels'

const PAGE_SIZE = 50
const PRESETS = RANGE_PRESETS.filter((preset) => preset.value !== 'personalizado')

function When({ at }: { at: string }) {
  return (
    <time dateTime={at} className="ap-ref whitespace-nowrap text-ap-ink">
      {formatDateTime(at)}
    </time>
  )
}

/** Detalle de solo lectura de un evento: quién, qué, sobre qué objeto, cuándo y desde dónde. */
function DetailSheet({ entry, onClose }: { entry?: AuditEntry; onClose: () => void }) {
  const metadata = entry ? Object.keys(entry.metadata ?? {}).length > 0 : false
  return (
    <Sheet open={entry !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-md">
        {entry ? (
          <>
            <SheetHeader className="border-b border-ap-line p-4 pr-16">
              <p className="ap-index">Evento de auditoría</p>
              <SheetTitle className="text-block-title text-ap-ink">{auditActionLabel(entry.action)}</SheetTitle>
              <SheetDescription>
                {entry.actor.email ?? 'Sistema'} · <When at={entry.occurredAt} />
              </SheetDescription>
              {AUDIT_WARNING.has(entry.action) ? (
                <div className="mt-1">
                  <StatusBadge tone="warning">Para revisar</StatusBadge>
                </div>
              ) : null}
            </SheetHeader>
            <dl className="flex flex-col gap-3 overflow-y-auto p-4 text-sm">
              {(
                [
                  ['Fecha y hora', <When key="t" at={entry.occurredAt} />, false],
                  ['Actor', entry.actor.email ?? 'Sistema (sin cuenta)', false],
                  ['ID de la cuenta', entry.actor.accountId ?? '—', true],
                  ['Acción', entry.action, true],
                  ['Objeto', auditEntityLabel(entry.entityType), false],
                  ['ID del objeto', entry.entityId ?? '—', true],
                  ['Dirección IP', entry.ipAddress ?? '—', true],
                ] as const
              ).map(([term, value, mono]) => (
                <div key={term} className="flex flex-col gap-0.5 border-b border-ap-line pb-2">
                  <dt className="text-ap-muted">{term}</dt>
                  <dd className={mono ? 'ap-ref break-all text-ap-ink' : 'break-all text-ap-ink'}>{value}</dd>
                </div>
              ))}
              <div className="flex flex-col gap-1">
                <dt className="text-ap-muted">Datos registrados</dt>
                <dd>
                  {metadata ? (
                    <pre className="overflow-x-auto rounded-md border border-ap-line bg-ap-paper p-3 font-mono text-xs leading-relaxed text-ap-ink">{JSON.stringify(entry.metadata, null, 2)}</pre>
                  ) : (
                    <p className="text-ap-muted">El evento no registró datos adicionales.</p>
                  )}
                </dd>
              </div>
            </dl>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  )
}

/**
 * `/admin/auditoria`: trazabilidad (§7.4). Cada fila responde quién (actor), qué (acción), sobre qué objeto y
 * cuándo (fecha en mono). Filtros en la URL y paginación en el servidor; el detalle es de solo lectura.
 */
export default function AdminAuditPage() {
  const [params, setParams] = useSearchParams()
  const preset: RangePreset = isRangePreset(params.get('rango')) && params.get('rango') !== 'personalizado' ? (params.get('rango') as RangePreset) : '7'
  const action = params.get('accion') ?? ''
  const entityType = params.get('entidad') ?? ''
  const actor = params.get('actor') ?? ''
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  const range = resolveRange(preset, {})
  const [selected, setSelected] = useState<AuditEntry>()
  const [actorDraft, setActorDraft] = useState(actor)

  const actions = useAsync(`${ADMIN_CACHE_PREFIX}audit:actions`, async () => (await getAdminInsightsService()).auditActions())
  const key = `${ADMIN_CACHE_PREFIX}audit:${range.from}:${range.to}:${action}:${entityType}:${actor}:${page}`
  const report = useAsync(key, async () =>
    (await getAdminInsightsService()).audit({ from: range.from, to: range.to, action, entityType, actor, page, size: PAGE_SIZE }),
  )

  function update(next: Record<string, string>, resetPage = true) {
    const merged = new URLSearchParams(params)
    for (const [name, value] of Object.entries(next)) {
      if (value) merged.set(name, value)
      else merged.delete(name)
    }
    if (resetPage) merged.delete('pagina')
    setParams(merged, { replace: true })
  }

  function clearFilters() {
    setActorDraft('')
    update({ accion: '', entidad: '', actor: '' })
  }

  const actionOptions = [...new Map((actions.data ?? []).map((option) => [option.action, option])).values()]
  const entityOptions = [...new Set((actions.data ?? []).map((option) => option.entityType))]
  const activeFilters = [action, entityType, actor].filter(Boolean).length
  const periodLabel = PRESETS.find((option) => option.value === preset)?.label ?? ''

  const columns: DataColumn<AuditEntry>[] = [
    { id: 'fecha', header: 'Fecha', cell: (row) => <When at={row.occurredAt} /> },
    {
      id: 'actor',
      header: 'Actor',
      cell: (row) => (row.actor.email ? <span className="break-all text-ap-ink">{row.actor.email}</span> : <span className="text-ap-muted">Sistema</span>),
    },
    {
      id: 'accion',
      header: 'Acción',
      rowHeader: true,
      cell: (row) => (
        <div className="flex flex-col items-start gap-1">
          <span className="font-semibold text-ap-ink">{auditActionLabel(row.action)}</span>
          {AUDIT_WARNING.has(row.action) ? <StatusBadge tone="warning">Para revisar</StatusBadge> : null}
        </div>
      ),
    },
    {
      id: 'objeto',
      header: 'Objeto',
      className: 'hidden md:table-cell',
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-ap-ink">{auditEntityLabel(row.entityType)}</p>
          {row.entityId ? (
            <p className="ap-ref max-w-[16rem] truncate text-ap-muted" title={row.entityId}>
              {row.entityId}
            </p>
          ) : null}
        </div>
      ),
    },
    { id: 'ip', header: 'IP', className: 'hidden lg:table-cell', cell: (row) => <span className="ap-ref text-ap-muted">{row.ipAddress ?? '—'}</span> },
    {
      id: 'detalle',
      header: 'Detalle',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button variant="outline" className="h-11 px-3" aria-label={`Ver detalle de ${auditActionLabel(row.action)} del ${formatDateTime(row.occurredAt)}`} onClick={() => setSelected(row)}>
          Ver
        </Button>
      ),
    },
  ]

  return (
    <AdminPage title="Auditoría" description="Quién hizo qué y cuándo: ingresos, cambios de administración y acciones sensibles en Autopartco.">
      <form
        role="search"
        aria-label="Filtrar auditoría"
        onSubmit={(event) => {
          event.preventDefault()
          update({ actor: actorDraft.trim() })
        }}
        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
      >
        <SelectField id="audit-rango" label="Periodo" options={PRESETS} value={preset} onChange={(event) => update({ rango: event.target.value })} />
        <SelectField
          id="audit-accion"
          label="Acción"
          placeholder="Todas"
          hint={actions.status === 'error' ? 'No pudimos cargar la lista de acciones.' : undefined}
          disabled={actions.data === undefined}
          options={actionOptions.map((option) => ({ value: option.action, label: auditActionLabel(option.action) }))}
          value={action}
          onChange={(event) => update({ accion: event.target.value })}
        />
        <SelectField
          id="audit-entidad"
          label="Objeto"
          placeholder="Todos"
          disabled={actions.data === undefined}
          options={entityOptions.map((value) => ({ value, label: auditEntityLabel(value) }))}
          value={entityType}
          onChange={(event) => update({ entidad: event.target.value })}
        />
        <div onBlur={() => actorDraft.trim() !== actor && update({ actor: actorDraft.trim() })}>
          <SearchField label="Actor (correo)" value={actorDraft} onChange={setActorDraft} placeholder="correo@ejemplo.co" />
        </div>
      </form>

      <div className="mt-4 mb-4 flex min-h-11 flex-wrap items-center justify-between gap-2 border-b border-ap-line pb-3">
        <p className="ap-ref text-ap-muted">
          {periodLabel} · {range.from} – {range.to}
          {activeFilters > 0 ? ` · ${pluralize(activeFilters, 'filtro activo', 'filtros activos')}` : ''}
        </p>
        {activeFilters > 0 ? (
          <Button variant="ghost" className="h-11 px-3 underline underline-offset-4" onClick={clearFilters}>
            Quitar filtros
          </Button>
        ) : null}
      </div>

      <AdminBoundary result={report} what="el historial de auditoría" keepPrevious skeleton={<ListSkeleton rows={8} label="Cargando el historial de auditoría" />}>
        {(data) =>
          data.page.total === 0 ? (
            activeFilters > 0 ? (
              <EmptyState icon={<SearchXIcon />} title="Ningún evento coincide" description="Prueba con otro periodo o quita los filtros de acción, objeto o actor.">
                <Button variant="outline" className="h-11 px-4" onClick={clearFilters}>
                  Quitar filtros
                </Button>
              </EmptyState>
            ) : (
              <EmptyState
                icon={<ScrollTextIcon />}
                title="Sin eventos en este periodo"
                description="Los ingresos y los cambios de administración aparecerán aquí. Prueba con un periodo más largo."
              />
            )
          ) : (
            <div className="flex flex-col gap-4">
              <DataTable caption="Eventos de auditoría" columns={columns} rows={data.page.items} getRowId={(row) => row.id} onRowClick={setSelected} />
              <ServerPager
                page={data.page.page}
                totalPages={Math.max(1, Math.ceil(data.page.total / data.page.size))}
                total={data.page.total}
                singular="evento"
                plural="eventos"
                busy={report.status === 'loading'}
                onPageChange={(next) => update({ pagina: String(next) }, false)}
              />
            </div>
          )
        }
      </AdminBoundary>
      <DetailSheet entry={selected} onClose={() => setSelected(undefined)} />
    </AdminPage>
  )
}
