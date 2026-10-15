import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { InfoIcon, MailPlusIcon, MoreHorizontalIcon, RefreshCwIcon, SearchXIcon, UserRoundXIcon, UsersIcon } from 'lucide-react'
import { ResultCount, SearchField } from '../components/search-field'
import { normalizeText } from '../lib/fuzzy'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { getStaffService } from '../api/staff-service'
import type { Staff, StaffInvitation, StaffMember } from '../api/types'
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

const MEMBER_COLUMNS: DataColumn<StaffMember>[] = [
  {
    id: 'nombre',
    header: 'Nombre',
    rowHeader: true,
    sortValue: (row) => `${row.name} ${row.surname}`,
    cell: (row) => <span className="font-semibold">{`${row.name} ${row.surname}`.trim()}</span>,
  },
  { id: 'correo', header: 'Correo', sortValue: (row) => row.email, cell: (row) => row.email },
  { id: 'roles', header: 'Rol', cell: (row) => row.roleNames.join(', ') || '—' },
  {
    id: 'estado',
    header: 'Estado',
    cell: (row) => (row.status === 'ACTIVE' ? <StatusBadge tone="success">Activo</StatusBadge> : <StatusBadge>{row.status}</StatusBadge>),
  },
]

function TeamView({ staff }: { staff: Staff }) {
  const { storeId, can, subscription, invalidateStore, promptUpgrade } = usePortal()
  const manage = can('COLLABORATOR_MANAGE')
  const [inviting, setInviting] = useState(false)
  const [toRevoke, setToRevoke] = useState<StaffInvitation>()
  const [busyId, setBusyId] = useState<string>()
  const usage = subscription?.usage.users
  const lacksRoles = subscription !== undefined && !subscription.capabilities.includes('CUSTOM_ROLES')
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

  const invitationColumns: DataColumn<StaffInvitation>[] = [
    { id: 'correo', header: 'Correo', rowHeader: true, sortValue: (row) => row.email, cell: (row) => <span className="font-semibold">{row.email}</span> },
    { id: 'rol', header: 'Rol', cell: (row) => row.roleName },
    {
      id: 'estado',
      header: 'Estado',
      cell: (row) => (row.expired ? <StatusBadge tone="danger">Vencida</StatusBadge> : <StatusBadge tone="warning">Pendiente</StatusBadge>),
    },
    { id: 'vence', header: 'Vence', sortValue: (row) => row.expiresAt, cell: (row) => formatDate(row.expiresAt) },
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
                  className="inline-flex size-10 items-center justify-center rounded-lg outline-none hover:bg-concrete data-popup-open:bg-concrete pointer-coarse:size-11"
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
        <UsageMeter capacity="USERS" used={usage.used} limit={usage.limit} upgradeTo={`/portal/${encodeURIComponent(storeId)}/plan`} className="max-w-md rounded-lg bg-card p-4 ring-1 ring-foreground/10" />
      ) : null}
      {manage && lacksRoles ? (
        <p role="note" className="flex items-start gap-2 rounded-lg bg-concrete p-3 text-sm">
          <InfoIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
          <span>
            Tu plan no incluye roles personalizados, así que por ahora no puedes invitar a más personas.{' '}
            <Link to={`/portal/${encodeURIComponent(storeId)}/plan`} className="font-semibold underline underline-offset-4">
              Ver planes
            </Link>
          </span>
        </p>
      ) : null}

      <section aria-labelledby="miembros-titulo" className="flex flex-col gap-3">
        <h2 id="miembros-titulo" className="text-xl font-extrabold tracking-tight">
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
              <DataTable caption="Colaboradores del almacén" columns={MEMBER_COLUMNS} rows={members} getRowId={(row) => row.id} pageSize={10} />
            )}
          </>
        )}
      </section>

      <section aria-labelledby="invitaciones-titulo" className="flex flex-col gap-3">
        <h2 id="invitaciones-titulo" className="text-xl font-extrabold tracking-tight">
          Invitaciones
        </h2>
        {staff.invitations.length === 0 ? (
          <EmptyState icon={<MailPlusIcon />} title="No hay invitaciones pendientes" description={manage ? 'Invita a tu equipo para repartir el trabajo: pedidos, productos o inventario.' : 'El propietario o un administrador puede invitar personas.'} className="py-8">
            {manage ? (
              <Button className="h-11 px-5" onClick={() => setInviting(true)}>
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

/** `/portal/:storeId/equipo`: colaboradores e invitaciones; invitar, reenviar y revocar. */
export default function PortalTeamPage() {
  const { can } = usePortal()
  const [inviting, setInviting] = useState(false)
  const staff = useStoreQuery('staff', async (id) => (await getStaffService()).list(id))

  return (
    <>
      <title>Equipo | Autopartco</title>
      <PageHeader
        title="Equipo"
        description="Las personas que trabajan contigo en el almacén y lo que puede hacer cada una."
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
        {(data) => <TeamView staff={data} />}
      </QueryBoundary>
      <InviteDialog open={inviting} onOpenChange={setInviting} />
    </>
  )
}
