import { useState } from 'react'
import { toast } from 'sonner'
import { LockIcon, LogOutIcon, SearchXIcon, ShieldUserIcon, UnlockIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { formatNumber } from '@/lib/format'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { ConfirmDialog } from '@/features/portal/components/confirm-dialog'
import { SelectField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { ServerPager } from '@/features/portal/components/server-pager'
import { formatDate, formatDateTime } from '@/features/portal/lib/labels'
import { getAdminConsoleService } from '../api/admin-console-service'
import type { AdminAccountDetail, AdminAccountRow } from '../api/console-types'
import { AdminBoundary, AdminPage } from '../components/admin-kit'
import { KeyValues, ListMeta, Pill, QuerySearch, SheetBlock } from '../components/console-kit'
import { useUrlFilters } from '../lib/use-url-filters'
import { ADMIN_CACHE_PREFIX } from '../hooks'
import { useAdminAccess } from '../lib/access'
import { ACCOUNT_TYPE_LABEL, stateLabel } from '../lib/console-labels'

const PAGE_SIZE = 25
const KEY = `${ADMIN_CACHE_PREFIX}accounts`
const TYPE_OPTIONS = ['CUSTOMER', 'STORE_MANAGER', 'STAFF', 'ROOT'].map((value) => ({ value, label: ACCOUNT_TYPE_LABEL[value] }))
const STATE_OPTIONS = ['ACTIVE', 'PENDING', 'LOCKED', 'INACTIVE'].map((value) => ({ value, label: stateLabel(value).label }))

type Action = 'lock' | 'unlock' | 'revoke'

const ACTION_TEXT: Record<Action, { title: string; description: string; confirm: string; done: string }> = {
  lock: {
    title: 'Desactivar la cuenta',
    description: 'La persona no podrá ingresar y se cerrarán todas sus sesiones. Puedes activarla de nuevo en cualquier momento.',
    confirm: 'Desactivar',
    done: 'Desactivaste la cuenta.',
  },
  unlock: {
    title: 'Activar la cuenta',
    description: 'La persona podrá volver a ingresar. Si estaba bloqueada por intentos fallidos, también se desbloquea.',
    confirm: 'Activar',
    done: 'Activaste la cuenta.',
  },
  revoke: {
    title: 'Cerrar todas las sesiones',
    description: 'La persona tendrá que volver a ingresar en todos sus dispositivos. Útil si reporta un acceso que no reconoce.',
    confirm: 'Cerrar sesiones',
    done: 'Cerraste todas las sesiones de la cuenta.',
  },
}

function AccountSheet({ accountId, onClose }: { accountId?: string; onClose: () => void }) {
  const access = useAdminAccess()
  const [action, setAction] = useState<Action>()
  const detail = useAsync(`${KEY}:detail:${accountId ?? 'none'}`, async () => (accountId ? getAdminConsoleService().account(accountId) : undefined))
  const account = detail.data?.account
  const protectedAccount = account ? account.type === 'ROOT' || (account.type === 'STAFF' && !access.root) : true
  const canManage = access.can('PLATFORM_ACCOUNTS_MANAGE') && !protectedAccount

  async function run() {
    if (!accountId || !action) return
    const service = getAdminConsoleService()
    if (action === 'lock') await service.lockAccount(accountId)
    else if (action === 'unlock') await service.unlockAccount(accountId)
    else await service.revokeSessions(accountId)
    toast.success(ACTION_TEXT[action].done)
    invalidateAsync(KEY)
  }

  return (
    <Sheet open={accountId !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-xl">
        <SheetHeader className="border-b border-ap-line p-4 pr-16">
          <p className="ap-index">Cuenta · {account ? ACCOUNT_TYPE_LABEL[account.type] ?? account.type : ''}</p>
          <SheetTitle className="text-block-title break-all text-ap-ink">{account?.email ?? 'Cargando…'}</SheetTitle>
          <SheetDescription>{account?.name ?? 'Detalle de la cuenta'}</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-5 overflow-y-auto p-4">
          <AdminBoundary result={detail} what="la cuenta" skeleton={<ListSkeleton rows={6} label="Cargando la cuenta" />}>
            {(data?: AdminAccountDetail) =>
              data ? (
                <>
                  <KeyValues
                    items={[
                      ['Estado', <Pill key="s" value={stateLabel(data.account.state)} />],
                      ['Tipo', ACCOUNT_TYPE_LABEL[data.account.type] ?? data.account.type],
                      ['Último ingreso', formatDateTime(data.account.lastLoginAt)],
                      ['Intentos fallidos (7 días)', data.failedLogins7d > 0 ? <span key="f" className="font-semibold text-ap-warning">{formatNumber(data.failedLogins7d)}</span> : '0'],
                      ['Verificación en dos pasos', data.twoFactor ? 'Activada' : 'Desactivada'],
                      ['Teléfono', data.phone ?? '—'],
                      ['Pedidos', formatNumber(data.account.orders)],
                      ['Alta', formatDate(data.account.createdAt)],
                      ['ID', data.account.id, true],
                    ]}
                  />
                  {canManage ? (
                    <div className="flex flex-wrap gap-2">
                      {data.account.state === 'ACTIVE' ? (
                        <Button variant="outline" className="h-11 px-4" onClick={() => setAction('lock')}>
                          <LockIcon aria-hidden />
                          Desactivar
                        </Button>
                      ) : (
                        <Button variant="outline" className="h-11 px-4" onClick={() => setAction('unlock')}>
                          <UnlockIcon aria-hidden />
                          Activar
                        </Button>
                      )}
                      <Button variant="outline" className="h-11 px-4" disabled={data.sessions.length === 0} onClick={() => setAction('revoke')}>
                        <LogOutIcon aria-hidden />
                        Cerrar sesiones
                      </Button>
                    </div>
                  ) : protectedAccount ? (
                    <p className="text-sm text-ap-muted">{data.account.type === 'ROOT' ? 'La cuenta root no se modifica desde la consola.' : 'Las cuentas del equipo se gestionan en Equipo (solo root).'}</p>
                  ) : null}
                  {data.stores.length > 0 ? (
                    <SheetBlock title="Almacenes">
                      <ul className="divide-y divide-ap-line border-y border-ap-line text-sm">
                        {data.stores.map((store) => (
                          <li key={store.storeId} className="flex items-center justify-between gap-3 py-2">
                            <span className="font-semibold text-ap-ink">{store.storeName}</span>
                            <span className="text-ap-muted">{store.roles ?? 'Sin rol'}</span>
                          </li>
                        ))}
                      </ul>
                    </SheetBlock>
                  ) : null}
                  <SheetBlock title={`Sesiones activas (${data.sessions.length})`}>
                    {data.sessions.length === 0 ? (
                      <p className="text-sm text-ap-muted">No tiene sesiones abiertas.</p>
                    ) : (
                      <ul className="divide-y divide-ap-line border-y border-ap-line text-sm">
                        {data.sessions.map((session) => (
                          <li key={session.id} className="flex flex-col gap-0.5 py-2">
                            <span className="text-ap-ink">{session.userAgent ?? 'Dispositivo desconocido'}</span>
                            <span className="ap-ref text-ap-muted">
                              {session.ipAddress ?? '—'} · desde {formatDateTime(session.createdAt)}
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </SheetBlock>
                  <SheetBlock title="Ingresos recientes">
                    {data.logins.length === 0 ? (
                      <p className="text-sm text-ap-muted">Sin ingresos registrados.</p>
                    ) : (
                      <ul className="divide-y divide-ap-line border-y border-ap-line text-sm">
                        {data.logins.map((login, index) => (
                          <li key={`${login.attemptedAt}-${index}`} className="flex items-center justify-between gap-3 py-2">
                            <span className="ap-ref text-ap-ink">{formatDateTime(login.attemptedAt)}</span>
                            <span className="flex items-center gap-2">
                              <span className="ap-ref text-ap-muted">{login.ipAddress ?? '—'}</span>
                              <Pill value={login.successful ? { label: 'Correcto', tone: 'success' } : { label: 'Fallido', tone: 'danger' }} />
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </SheetBlock>
                </>
              ) : null
            }
          </AdminBoundary>
        </div>
        {action ? (
          <ConfirmDialog
            open
            onOpenChange={(open) => !open && setAction(undefined)}
            title={ACTION_TEXT[action].title}
            description={ACTION_TEXT[action].description}
            confirmLabel={ACTION_TEXT[action].confirm}
            destructive={action !== 'unlock'}
            onConfirm={async () => {
              await run()
              setAction(undefined)
            }}
          />
        ) : null}
      </SheetContent>
    </Sheet>
  )
}

/** `/admin/cuentas`: buscar una cuenta (correo, nombre o identificación), ver su actividad y desactivarla o cerrar sesiones. */
export default function AdminAccountsPage() {
  const { get, page, update } = useUrlFilters()
  const q = get('q')
  const type = get('tipo')
  const state = get('estado')
  const [selected, setSelected] = useState<string>()
  const list = useAsync(`${KEY}:list:${q}:${type}:${state}:${page}`, () => getAdminConsoleService().accounts({ q, type, state, page, size: PAGE_SIZE }))
  const activeFilters = [q, type, state].filter(Boolean).length

  const columns: DataColumn<AdminAccountRow>[] = [
    {
      id: 'cuenta',
      header: 'Cuenta',
      rowHeader: true,
      cell: (row) => (
        <div className="min-w-0">
          <p className="break-all font-semibold text-ap-ink">{row.email}</p>
          <p className="text-sm text-ap-muted">{row.name ?? '—'}</p>
        </div>
      ),
    },
    { id: 'tipo', header: 'Tipo', cell: (row) => <span className="text-ap-ink">{ACCOUNT_TYPE_LABEL[row.type] ?? row.type}</span> },
    { id: 'estado', header: 'Estado', cell: (row) => <Pill value={stateLabel(row.state)} /> },
    { id: 'ingreso', header: 'Último ingreso', className: 'hidden md:table-cell', cell: (row) => <span className="ap-ref text-ap-ink">{formatDateTime(row.lastLoginAt)}</span> },
    { id: 'sesiones', header: 'Sesiones', align: 'end', className: 'hidden lg:table-cell', cell: (row) => <span className="ap-num">{formatNumber(row.activeSessions)}</span> },
    {
      id: 'ver',
      header: 'Detalle',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button variant="outline" className="h-11 px-3" aria-label={`Ver la cuenta ${row.email}`} onClick={() => setSelected(row.id)}>
          Ver
        </Button>
      ),
    },
  ]

  return (
    <AdminPage title="Cuentas" description="Usuarios de la plataforma: actividad, sesiones abiertas e ingresos, con acciones de soporte.">
      <form role="search" aria-label="Filtrar cuentas" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <QuerySearch label="Correo, nombre o identificación" placeholder="correo@ejemplo.co" value={q} onApply={(value) => update({ q: value })} />
        <SelectField id="cuentas-tipo" label="Tipo" placeholder="Todos" options={TYPE_OPTIONS} value={type} onChange={(event) => update({ tipo: event.target.value })} />
        <SelectField id="cuentas-estado" label="Estado" placeholder="Todos" options={STATE_OPTIONS} value={state} onChange={(event) => update({ estado: event.target.value })} />
      </form>
      <ListMeta total={list.data?.total} singular="cuenta" plural="cuentas" activeFilters={activeFilters} onClear={() => update({ q: '', tipo: '', estado: '' })} />
      <AdminBoundary result={list} what="las cuentas" keepPrevious skeleton={<ListSkeleton rows={8} label="Cargando las cuentas" />}>
        {(data) =>
          data.total === 0 ? (
            activeFilters > 0 ? (
              <EmptyState icon={<SearchXIcon />} title="Ninguna cuenta coincide" description="Prueba con otro correo, nombre o filtro." />
            ) : (
              <EmptyState icon={<ShieldUserIcon />} title="Aún no hay cuentas" description="Las cuentas aparecen cuando las personas se registran." />
            )
          ) : (
            <div className="flex flex-col gap-4">
              <DataTable caption="Cuentas de la plataforma" columns={columns} rows={data.items} getRowId={(row) => row.id} onRowClick={(row) => setSelected(row.id)} />
              <ServerPager page={data.page} totalPages={Math.max(1, data.totalPages)} total={data.total} singular="cuenta" plural="cuentas" busy={list.status === 'loading'} onPageChange={(next) => update({ pagina: String(next) }, false)} />
            </div>
          )
        }
      </AdminBoundary>
      <AccountSheet accountId={selected} onClose={() => setSelected(undefined)} />
    </AdminPage>
  )
}
