import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { MailPlusIcon, MoreHorizontalIcon, RefreshCwIcon, SearchXIcon, ShieldCheckIcon, UserRoundCheckIcon, UserRoundXIcon, UsersIcon } from 'lucide-react'
import { ResultCount, SearchField } from '../components/search-field'
import { normalizeText } from '../lib/fuzzy'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button, buttonVariants } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { getStaffService } from '../api/staff-service'
import type { Staff, StaffInvitation, StaffMember, StaffRole } from '../api/types'
import { ChangeRoleDialog } from '../components/change-role-dialog'
import { ConfirmDialog } from '../components/confirm-dialog'
import { InviteDialog } from '../components/invite-dialog'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { UsageMeter } from '../components/usage-meter'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { actionErrorMessage } from '../lib/errors'
import { formatDate } from '../lib/labels'
import { roleName } from '../lib/staff-roles'
import { useSession } from '@/features/auth/hooks'

function memberRole(member: StaffMember, roles: readonly StaffRole[] | undefined): string {
  const fallback = member.roleNames.join(', ') || '—'
  if (member.roleCode === null) return member.roleNames.length > 0 ? `${fallback} (personalizado)` : fallback
  return roleName(roles, member.roleCode, fallback)
}

function TeamView({ staff, roles }: { staff: Staff; roles: StaffRole[] | undefined }) {
  const { storeId, can, subscription, invalidateStore, promptUpgrade } = usePortal()
  const manage = can('COLLABORATOR_MANAGE')
  const myEmail = useSession()?.user.email.toLowerCase()
  const [inviting, setInviting] = useState(false)
  const [toRevoke, setToRevoke] = useState<StaffInvitation>()
  const [toChangeRole, setToChangeRole] = useState<StaffMember>()
  const [toDeactivate, setToDeactivate] = useState<StaffMember>()
  const [busyId, setBusyId] = useState<string>()
  const usage = subscription?.usage.users
  const [memberQuery, setMemberQuery] = useState('')
  const members = useMemo(() => {
    const term = normalizeText(memberQuery)
    return staff.members.filter((member) => !term || normalizeText(`${member.name} ${member.surname} ${member.email}`).includes(term))
  }, [staff.members, memberQuery])

  async function resend(invitation: StaffInvitation) {
    setBusyId(invitation.id)
    try {
      await (await getStaffService()).resend(storeId, invitation.id)
      invalidateStore()
      toast.success(`Reenviamos la invitación a ${invitation.email}.`)
    } catch (error) {
      if (!promptUpgrade(error)) toast.error(actionErrorMessage(error, 'No pudimos reenviar la invitación.'))
    } finally {
      setBusyId(undefined)
    }
  }

  async function reactivate(member: StaffMember) {
    setBusyId(member.id)
    try {
      await (await getStaffService()).setActive(storeId, member.id, true)
      invalidateStore()
      toast.success(`${member.name} vuelve a tener acceso al almacén.`)
    } catch (error) {
      if (!promptUpgrade(error)) toast.error(actionErrorMessage(error, 'No pudimos reactivar al colaborador.'))
    } finally {
      setBusyId(undefined)
    }
  }

  const manageable = (member: StaffMember) => manage && !member.owner && member.email.toLowerCase() !== myEmail

  const memberColumns: DataColumn<StaffMember>[] = [
    {
      id: 'nombre',
      header: 'Nombre',
      rowHeader: true,
      sortValue: (row) => `${row.name} ${row.surname}`,
      cell: (row) => (
        <span className="flex flex-col">
          <span className="font-semibold text-ap-ink">{`${row.name} ${row.surname}`.trim()}</span>
          {row.email.toLowerCase() === myEmail ? <span className="text-xs text-ap-muted">Tú</span> : null}
        </span>
      ),
    },
    { id: 'correo', header: 'Correo', sortValue: (row) => row.email, cell: (row) => row.email },
    { id: 'roles', header: 'Rol', sortValue: (row) => memberRole(row, roles), cell: (row) => memberRole(row, roles) },
    {
      id: 'estado',
      header: 'Estado',
      cell: (row) => (row.status === 'ACTIVE' ? <StatusBadge tone="success">Activo</StatusBadge> : <StatusBadge>Desactivado</StatusBadge>),
    },
    ...(manage
      ? [
          {
            id: 'acciones',
            header: 'Acciones',
            actions: true,
            align: 'end' as const,
            cell: (row: StaffMember) =>
              manageable(row) ? (
                <DropdownMenu>
                  <DropdownMenuTrigger
                    aria-label={`Acciones para ${row.name} ${row.surname}`.trim()}
                    className={buttonVariants({ variant: 'ghost', size: 'icon' })}
                  >
                    <MoreHorizontalIcon className="size-5" aria-hidden />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent>
                    <DropdownMenuItem disabled={roles === undefined} onClick={() => setToChangeRole(row)}>
                      <ShieldCheckIcon aria-hidden />
                      Cambiar rol
                    </DropdownMenuItem>
                    {row.status === 'ACTIVE' ? (
                      <DropdownMenuItem variant="destructive" onClick={() => setToDeactivate(row)}>
                        <UserRoundXIcon aria-hidden />
                        Desactivar acceso
                      </DropdownMenuItem>
                    ) : (
                      <DropdownMenuItem disabled={busyId === row.id} onClick={() => void reactivate(row)}>
                        <UserRoundCheckIcon aria-hidden />
                        Reactivar acceso
                      </DropdownMenuItem>
                    )}
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <span className="text-xs text-ap-muted">{row.owner ? 'Propietario' : '—'}</span>
              ),
          },
        ]
      : []),
  ]

  const invitationColumns: DataColumn<StaffInvitation>[] = [
    { id: 'correo', header: 'Correo', rowHeader: true, sortValue: (row) => row.email, cell: (row) => <span className="font-semibold text-ap-ink">{row.email}</span> },
    { id: 'rol', header: 'Rol', cell: (row) => roleName(roles, row.roleCode, row.roleName) },
    {
      id: 'estado',
      header: 'Estado',
      cell: (row) => (row.expired ? <StatusBadge tone="danger">Vencida</StatusBadge> : <StatusBadge tone="warning">Pendiente</StatusBadge>),
    },
    { id: 'vence', header: 'Vence', sortValue: (row) => row.expiresAt, cell: (row) => <span className="ap-ref">{formatDate(row.expiresAt)}</span> },
    ...(manage
      ? [
          {
            id: 'acciones',
            header: 'Acciones',
            actions: true,
            align: 'end' as const,
            cell: (row: StaffInvitation) => (
              <DropdownMenu>
                <DropdownMenuTrigger
                  aria-label={`Acciones para la invitación de ${row.email}`}
                  className={buttonVariants({ variant: 'ghost', size: 'icon' })}
                >
                  <MoreHorizontalIcon className="size-5" aria-hidden />
                </DropdownMenuTrigger>
                <DropdownMenuContent>
                  <DropdownMenuItem disabled={busyId === row.id} onClick={() => void resend(row)}>
                    <RefreshCwIcon aria-hidden />
                    Reenviar invitación
                  </DropdownMenuItem>
                  <DropdownMenuItem variant="destructive" onClick={() => setToRevoke(row)}>
                    <UserRoundXIcon aria-hidden />
                    Revocar
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ),
          },
        ]
      : []),
  ]

  return (
    <div className="flex flex-col gap-8">
      {usage ? (
        <UsageMeter capacity="USERS" used={usage.used} limit={usage.limit} upgradeTo={`/portal/${encodeURIComponent(storeId)}/plan`} className="max-w-md rounded-lg border border-ap-line p-4" />
      ) : null}
      <section aria-labelledby="miembros-titulo" className="flex flex-col gap-3">
        <h2 id="miembros-titulo" className="text-block-title text-ap-ink">
          Colaboradores
        </h2>
        {staff.members.length === 0 ? (
          <EmptyState icon={<UsersIcon />} title="Aún no hay colaboradores" description="Cuando alguien acepte una invitación aparecerá aquí." className="py-8" />
        ) : (
          <>
            {staff.members.length > 5 ? (
              <form role="search" aria-label="Buscar colaboradores" onSubmit={(event) => event.preventDefault()} className="max-w-md">
                <SearchField value={memberQuery} onChange={setMemberQuery} placeholder="Nombre o correo" />
              </form>
            ) : null}
            <ResultCount shown={members.length} total={staff.members.length} singular="colaborador" plural="colaboradores" />
            {members.length === 0 ? (
              <EmptyState icon={<SearchXIcon />} title="Nadie coincide con la búsqueda" description="Revisa el nombre o el correo." className="py-8">
                <Button variant="outline" className="h-11 px-4" onClick={() => setMemberQuery('')}>
                  Quitar búsqueda
                </Button>
              </EmptyState>
            ) : (
              <DataTable caption="Colaboradores del almacén" columns={memberColumns} rows={members} getRowId={(row) => row.id} pageSize={10} />
            )}
          </>
        )}
      </section>

      <section aria-labelledby="invitaciones-titulo" className="flex flex-col gap-3">
        <h2 id="invitaciones-titulo" className="text-block-title text-ap-ink">
          Invitaciones
        </h2>
        {staff.invitations.length === 0 ? (
          <EmptyState icon={<MailPlusIcon />} title="No hay invitaciones pendientes" description={manage ? 'Invita a tu equipo para repartir el trabajo: pedidos, productos o inventario.' : 'El propietario o un administrador puede invitar personas.'} className="py-8">
            {manage ? (
              <Button variant="outline" className="h-11 px-4" onClick={() => setInviting(true)}>
                <MailPlusIcon aria-hidden />
                Invitar colaborador
              </Button>
            ) : null}
          </EmptyState>
        ) : (
          <DataTable caption="Invitaciones enviadas" columns={invitationColumns} rows={staff.invitations} getRowId={(row) => row.id} />
        )}
      </section>

      <InviteDialog open={inviting} onOpenChange={setInviting} />
      {roles ? <ChangeRoleDialog member={toChangeRole} roles={roles} onClose={() => setToChangeRole(undefined)} /> : null}
      <ConfirmDialog
        open={toDeactivate !== undefined}
        onOpenChange={(open) => !open && setToDeactivate(undefined)}
        title={`¿Desactivar el acceso de ${toDeactivate ? `${toDeactivate.name} ${toDeactivate.surname}`.trim() : ''}?`}
        description="Dejará de entrar al portal de este almacén desde su próxima acción y liberará un usuario del plan. Conserva su rol: puedes reactivarlo cuando quieras."
        confirmLabel="Desactivar acceso"
        destructive
        onConfirm={async () => {
          if (!toDeactivate) return
          await (await getStaffService()).setActive(storeId, toDeactivate.id, false)
          invalidateStore()
          toast.success('Acceso desactivado.')
        }}
      />
      <ConfirmDialog
        open={toRevoke !== undefined}
        onOpenChange={(open) => !open && setToRevoke(undefined)}
        title={`¿Revocar la invitación de ${toRevoke?.email ?? ''}?`}
        description="El enlace que ya recibió dejará de funcionar. Si cambias de opinión, puedes invitarla de nuevo."
        confirmLabel="Revocar invitación"
        destructive
        onConfirm={async () => {
          if (!toRevoke) return
          await (await getStaffService()).revoke(storeId, toRevoke.id)
          invalidateStore()
          toast.success('Invitación revocada.')
        }}
      />
    </div>
  )
}

/** `/portal/:storeId/equipo`: colaboradores e invitaciones; invitar, cambiar rol, desactivar, reenviar y revocar. */
export default function PortalTeamPage() {
  const { can } = usePortal()
  const [inviting, setInviting] = useState(false)
  const staff = useStoreQuery('staff', async (id) => (await getStaffService()).list(id))
  const roles = useStoreQuery('staff-roles', async (id) => (await getStaffService()).roles(id))

  return (
    <>
      <title>Equipo | Autopartco</title>
      <PageHeader
        title="Equipo"
        description="Las personas que trabajan contigo en el almacén. Cada una tiene un rol que define qué puede ver y hacer."
        actions={
          can('COLLABORATOR_MANAGE') ? (
            <Button className="h-11 px-4" onClick={() => setInviting(true)}>
              <MailPlusIcon aria-hidden />
              Invitar colaborador
            </Button>
          ) : null
        }
      />
      <QueryBoundary result={staff} what="tu equipo">
        {(data) => <TeamView staff={data} roles={roles.data} />}
      </QueryBoundary>
      <InviteDialog open={inviting} onOpenChange={setInviting} />
    </>
  )
}
