import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { ScrollTextIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { useAsync } from '@/lib/use-async'
import { SelectField } from '@/features/portal/components/form-controls'
import { QueryBoundary } from '@/features/portal/components/query-boundary'
import { SearchField } from '@/features/portal/components/search-field'
import { ServerPager } from '@/features/portal/components/server-pager'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { RANGE_PRESETS, isRangePreset, resolveRange, type RangePreset } from '@/features/portal/lib/analytics'
import { formatDateTime } from '@/features/portal/lib/labels'
import { getAdminInsightsService } from '../api/admin-insights-service'
import type { AuditEntry } from '../api/insights-types'
import { AdminPage } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'
import { AUDIT_WARNING, auditActionLabel, auditEntityLabel } from '../lib/audit-labels'

const PAGE_SIZE = 50
const PRESETS = RANGE_PRESETS.filter((preset) => preset.value !== 'personalizado')

function DetailSheet({ entry, onClose }: { entry?: AuditEntry; onClose: () => void }) {
  return (
    <Sheet open={entry !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-md">
        {entry ? (
          <>
            <SheetHeader className="p-4 pr-16">
              <SheetTitle className="text-lg font-extrabold tracking-tight">{auditActionLabel(entry.action)}</SheetTitle>
              <SheetDescription>{formatDateTime(entry.occurredAt)}</SheetDescription>
            </SheetHeader>
            <dl className="flex flex-col gap-3 overflow-y-auto px-4 pb-6 text-sm">
              {[
                ['Actor', entry.actor.email ?? 'Sistema'],
                ['Entidad', auditEntityLabel(entry.entityType)],
                ['ID de la entidad', entry.entityId ?? '—'],
                ['IP', entry.ipAddress ?? '—'],
                ['Código', entry.action],
              ].map(([term, value]) => (
                <div key={term} className="flex flex-col gap-0.5 border-b border-border pb-2">
                  <dt className="text-muted-foreground">{term}</dt>
                  <dd className="font-mono break-all">{value}</dd>
                </div>
              ))}
              <div className="flex flex-col gap-1">
                <dt className="text-muted-foreground">Detalle</dt>
                <dd>
                  <pre className="overflow-x-auto rounded-lg bg-concrete p-3 font-mono text-xs leading-relaxed">{JSON.stringify(entry.metadata, null, 2)}</pre>
                </dd>
              </div>
            </dl>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  )
}

/** `/admin/auditoria`: historial de acciones relevantes, filtrable y paginado en el servidor. Los filtros viven en la URL. */
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

  const actionOptions = [...new Map((actions.data ?? []).map((option) => [option.action, option])).values()]
  const entityOptions = [...new Set((actions.data ?? []).map((option) => option.entityType))]

  const columns: DataColumn<AuditEntry>[] = [
    { id: 'fecha', header: 'Fecha', cell: (row) => <span className="whitespace-nowrap tabular-nums">{formatDateTime(row.occurredAt)}</span> },
    {
      id: 'accion',
      header: 'Acción',
      rowHeader: true,
      cell: (row) =>
        AUDIT_WARNING.has(row.action) ? <StatusBadge tone="warning">{auditActionLabel(row.action)}</StatusBadge> : <span className="font-semibold">{auditActionLabel(row.action)}</span>,
    },
    { id: 'actor', header: 'Actor', cell: (row) => <span className="break-all">{row.actor.email ?? 'Sistema'}</span> },
    { id: 'entidad', header: 'Entidad', className: 'hidden md:table-cell', cell: (row) => auditEntityLabel(row.entityType) },
    { id: 'ip', header: 'IP', className: 'hidden lg:table-cell', cell: (row) => <span className="font-mono text-sm">{row.ipAddress ?? '—'}</span> },
    {
      id: 'detalle',
      header: 'Detalle',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button variant="outline" className="h-10 px-3" aria-label={`Ver detalle de ${auditActionLabel(row.action)}`} onClick={() => setSelected(row)}>
          Ver
        </Button>
      ),
    },
  ]

  const filtersActive = Boolean(action || entityType || actor)

  return (
    <AdminPage label="Plataforma" title="Auditoría" description="Historial de ingresos, cambios de administración y acciones sensibles en Autopartco.">
      <form
        role="search"
        aria-label="Filtrar auditoría"
        onSubmit={(event) => {
          event.preventDefault()
          update({ actor: actorDraft.trim() })
        }}
        className="mb-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
      >
        <SelectField id="audit-rango" label="Periodo" options={PRESETS} value={preset} onChange={(event) => update({ rango: event.target.value })} />
        <SelectField
          id="audit-accion"
          label="Acción"
          placeholder="Todas"
          options={actionOptions.map((option) => ({ value: option.action, label: auditActionLabel(option.action) }))}
          value={action}
          onChange={(event) => update({ accion: event.target.value })}
        />
        <SelectField
          id="audit-entidad"
          label="Entidad"
          placeholder="Todas"
          options={entityOptions.map((value) => ({ value, label: auditEntityLabel(value) }))}
          value={entityType}
          onChange={(event) => update({ entidad: event.target.value })}
        />
        <div onBlur={() => actorDraft.trim() !== actor && update({ actor: actorDraft.trim() })}>
          <SearchField label="Actor (correo)" value={actorDraft} onChange={setActorDraft} placeholder="correo@ejemplo.co" />
        </div>
      </form>
      <QueryBoundary result={report} what="el historial de auditoría" keepPrevious>
        {(data) =>
          data.page.total === 0 ? (
            filtersActive ? (
              <EmptyState icon={<SearchXIcon />} title="Ningún evento coincide" description="Prueba con otro periodo o quita los filtros.">
                <Button
                  variant="outline"
                  className="h-11 px-4"
                  onClick={() => {
                    setActorDraft('')
                    update({ accion: '', entidad: '', actor: '' })
                  }}
                >
                  Quitar filtros
                </Button>
              </EmptyState>
            ) : (
              <EmptyState icon={<ScrollTextIcon />} title="Sin eventos en este periodo" description="Los ingresos y cambios de administración aparecerán aquí." />
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
      </QueryBoundary>
      <DetailSheet entry={selected} onClose={() => setSelected(undefined)} />
    </AdminPage>
  )
}
