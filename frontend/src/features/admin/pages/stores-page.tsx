import { useState } from 'react'
import { SearchXIcon, StoreIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { formatCOP, formatNumber } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { SelectField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { ServerPager } from '@/features/portal/components/server-pager'
import { formatDate } from '@/features/portal/lib/labels'
import { getAdminConsoleService } from '../api/admin-console-service'
import type { AdminStoreRow } from '../api/console-types'
import { AdminBoundary, AdminPage, FigureList } from '../components/admin-kit'
import { KeyValues, ListMeta, Pill, QuerySearch, SheetBlock } from '../components/console-kit'
import { useUrlFilters } from '../lib/use-url-filters'
import { ADMIN_CACHE_PREFIX } from '../hooks'
import { stateLabel } from '../lib/console-labels'

const PAGE_SIZE = 25
const STATE_OPTIONS = ['ACTIVE', 'PENDING', 'INACTIVE', 'REJECTED'].map((value) => ({ value, label: stateLabel(value).label }))
const BUSINESS_TYPE: Record<string, string> = { PARTS: 'Repuestos', SERVICES: 'Servicios', BOTH: 'Repuestos y servicios' }

function planLine(row: AdminStoreRow): string {
  if (!row.planName) return 'Sin plan'
  if (row.paidThrough) return `${row.planName} · pagado hasta ${formatDate(row.paidThrough)}`
  if (row.trialEndsAt) return `${row.planName} · prueba hasta ${formatDate(row.trialEndsAt)}`
  return row.planName
}

function StoreSheet({ storeId, onClose }: { storeId?: string; onClose: () => void }) {
  const detail = useAsync(`${ADMIN_CACHE_PREFIX}store:${storeId ?? 'none'}`, async () => (storeId ? getAdminConsoleService().store(storeId) : undefined))
  return (
    <Sheet open={storeId !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-xl">
        <SheetHeader className="border-b border-ap-line p-4 pr-16">
          <p className="ap-index">Almacén</p>
          <SheetTitle className="text-block-title text-ap-ink">{detail.data?.store.businessName ?? 'Cargando…'}</SheetTitle>
          <SheetDescription>{detail.data ? planLine(detail.data.store) : 'Detalle del almacén'}</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-5 overflow-y-auto p-4">
          <AdminBoundary result={detail} what="el almacén" skeleton={<ListSkeleton rows={6} label="Cargando el almacén" />}>
            {(data) =>
              data ? (
                <>
                  <FigureList
                    items={[
                      { label: 'Pedidos (30 días)', value: formatNumber(data.store.orders30d) },
                      { label: 'Ventas (30 días)', value: formatCOP(data.store.revenue30d) },
                      { label: 'Calificación', value: data.ratingAverage !== null ? `${formatNumber(data.ratingAverage)} / 5` : '—', hint: `${formatNumber(data.ratingCount)} reseñas` },
                    ]}
                  />
                  <KeyValues
                    items={[
                      ['Estado', <Pill key="s" value={stateLabel(data.store.state)} />],
                      ['Tipo', BUSINESS_TYPE[data.store.businessType ?? ''] ?? data.store.businessType ?? '—'],
                      ['NIT', data.store.nit ?? '—', true],
                      ['Correo', data.store.businessEmail ?? '—'],
                      ['Productos', `${formatNumber(data.store.products)} · ${formatNumber(data.publishedProducts)} publicados`],
                      ['Inventarios agotados', data.stockouts > 0 ? <span key="a" className="font-semibold text-ap-warning">{formatNumber(data.stockouts)}</span> : '0'],
                      ['Preguntas sin responder', formatNumber(data.openQuestions)],
                      ['Alta', formatDate(data.store.createdAt)],
                      ['ID', data.store.id, true],
                    ]}
                  />
                  <SheetBlock title={`Sucursales (${data.branches.length})`}>
                    <ul className="divide-y divide-ap-line border-y border-ap-line text-sm">
                      {data.branches.map((branch) => (
                        <li key={branch.id} className="flex items-center justify-between gap-3 py-2">
                          <span className="min-w-0">
                            <span className="block font-semibold text-ap-ink">{branch.name}</span>
                            <span className="text-ap-muted">{branch.municipality ?? 'Sin municipio'}</span>
                          </span>
                          {!branch.active ? <Pill value={{ label: 'Inactiva', tone: 'neutral' }} /> : null}
                        </li>
                      ))}
                    </ul>
                  </SheetBlock>
                  <SheetBlock title={`Equipo (${data.team.length})`}>
                    <ul className="divide-y divide-ap-line border-y border-ap-line text-sm">
                      {data.team.map((member, index) => (
                        <li key={`${member.email}-${index}`} className="flex items-start justify-between gap-3 py-2">
                          <span className="min-w-0">
                            <span className="block font-semibold text-ap-ink">{member.name}</span>
                            <span className="break-all text-ap-muted">{member.email ?? 'Sin cuenta'}</span>
                          </span>
                          <span className="shrink-0 text-right text-ap-ink">{member.roles ?? 'Sin rol'}</span>
                        </li>
                      ))}
                    </ul>
                  </SheetBlock>
                </>
              ) : null
            }
          </AdminBoundary>
        </div>
      </SheetContent>
    </Sheet>
  )
}

/** `/admin/almacenes`: buscar un almacén y ver su plan, actividad, sucursales y equipo (soporte). */
export default function AdminStoresPage() {
  const { get, page, update } = useUrlFilters()
  const q = get('q')
  const state = get('estado')
  const [selected, setSelected] = useState<string>()
  const list = useAsync(`${ADMIN_CACHE_PREFIX}stores:${q}:${state}:${page}`, () => getAdminConsoleService().stores({ q, state, page, size: PAGE_SIZE }))
  const activeFilters = [q, state].filter(Boolean).length

  const columns: DataColumn<AdminStoreRow>[] = [
    {
      id: 'almacen',
      header: 'Almacén',
      rowHeader: true,
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-semibold text-ap-ink">{row.businessName}</p>
          <p className="ap-ref text-ap-muted">{row.nit ?? 'Sin NIT'}</p>
        </div>
      ),
    },
    { id: 'estado', header: 'Estado', cell: (row) => <Pill value={stateLabel(row.state)} /> },
    { id: 'plan', header: 'Plan', className: 'hidden md:table-cell', cell: (row) => <span className="text-ap-ink">{planLine(row)}</span> },
    { id: 'productos', header: 'Productos', align: 'end', className: 'hidden lg:table-cell', cell: (row) => <span className="ap-num">{formatNumber(row.products)}</span> },
    { id: 'pedidos', header: 'Pedidos 30 d', align: 'end', cell: (row) => <span className="ap-num">{formatNumber(row.orders30d)}</span> },
    { id: 'ventas', header: 'Ventas 30 d', align: 'end', className: 'hidden sm:table-cell', cell: (row) => <span className="ap-num">{formatCOP(row.revenue30d)}</span> },
    {
      id: 'ver',
      header: 'Detalle',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button variant="outline" className="h-11 px-3" aria-label={`Ver ${row.businessName}`} onClick={() => setSelected(row.id)}>
          Ver
        </Button>
      ),
    },
  ]

  return (
    <AdminPage title="Almacenes" description="Plan, actividad, sucursales y equipo de cada almacén de la plataforma.">
      <form role="search" aria-label="Filtrar almacenes" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <QuerySearch label="Nombre, NIT o correo" placeholder="Autopartes…" value={q} onApply={(value) => update({ q: value })} />
        <SelectField id="almacenes-estado" label="Estado" placeholder="Todos" options={STATE_OPTIONS} value={state} onChange={(event) => update({ estado: event.target.value })} />
      </form>
      <ListMeta total={list.data?.total} singular="almacén" plural="almacenes" activeFilters={activeFilters} onClear={() => update({ q: '', estado: '' })} />
      <AdminBoundary result={list} what="los almacenes" keepPrevious skeleton={<ListSkeleton rows={8} label="Cargando los almacenes" />}>
        {(data) =>
          data.total === 0 ? (
            activeFilters > 0 ? (
              <EmptyState icon={<SearchXIcon />} title="Ningún almacén coincide" description="Prueba con otro nombre, NIT o estado." />
            ) : (
              <EmptyState icon={<StoreIcon />} title="Aún no hay almacenes" description="Los almacenes aparecen aquí cuando se aprueba su solicitud." />
            )
          ) : (
            <div className="flex flex-col gap-4">
              <DataTable caption="Almacenes de la plataforma" columns={columns} rows={data.items} getRowId={(row) => row.id} onRowClick={(row) => setSelected(row.id)} />
              <ServerPager page={data.page} totalPages={Math.max(1, data.totalPages)} total={data.total} singular="almacén" plural="almacenes" busy={list.status === 'loading'} onPageChange={(next) => update({ pagina: String(next) }, false)} />
            </div>
          )
        }
      </AdminBoundary>
      <StoreSheet storeId={selected} onClose={() => setSelected(undefined)} />
    </AdminPage>
  )
}
