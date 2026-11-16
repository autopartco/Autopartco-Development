import { useState } from 'react'
import { toast } from 'sonner'
import { PencilIcon, PlusIcon, PowerIcon, Trash2Icon, UserCogIcon, UserMinusIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { ConfirmDialog } from '@/features/portal/components/confirm-dialog'
import { SelectField, TextAreaField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { formatDateTime } from '@/features/portal/lib/labels'
import { getAdminConsoleService } from '../api/admin-console-service'
import type { NewStaffMember, PlatformPermission, StaffMember, StaffPermission, StaffRole } from '../api/console-types'
import { AdminBoundary, AdminPage, AdminSection, FormSheet, RowActions } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'
import { PERMISSION_LABEL } from '../lib/console-labels'

const KEY = `${ADMIN_CACHE_PREFIX}staff`
const EMPTY_MEMBER: NewStaffMember = { email: '', name: '', surname: '', identification: '', dateBirth: '', numberPhone: '', roleId: '' }

function reload() {
  invalidateAsync(KEY)
}

/** Alta de miembro: datos personales, correo y rol. La persona recibe un correo para definir su contraseña. */
function MemberSheet({ open, onOpenChange, roles }: { open: boolean; onOpenChange: (open: boolean) => void; roles: StaffRole[] }) {
  const [values, setValues] = useState<NewStaffMember>(EMPTY_MEMBER)
  const [errors, setErrors] = useState<Partial<Record<keyof NewStaffMember, string>>>({})
  const set = (name: keyof NewStaffMember, value: string) => setValues((current) => ({ ...current, [name]: value }))

  function validate(): boolean {
    const next: Partial<Record<keyof NewStaffMember, string>> = {}
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(values.email.trim())) next.email = 'Escribe un correo válido.'
    if (!values.name.trim()) next.name = 'Escribe el nombre.'
    if (!values.surname.trim()) next.surname = 'Escribe el apellido.'
    if (!/^[0-9A-Za-z-]{5,20}$/.test(values.identification.trim())) next.identification = 'Entre 5 y 20 caracteres, sin espacios.'
    if (!values.dateBirth || values.dateBirth >= new Date().toISOString().slice(0, 10)) next.dateBirth = 'Elige una fecha anterior a hoy.'
    if (!/^\+?[0-9 ().-]{7,20}$/.test(values.numberPhone.trim())) next.numberPhone = 'Escribe un teléfono válido.'
    if (!values.roleId) next.roleId = 'Elige un rol.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  return (
    <FormSheet
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          setValues(EMPTY_MEMBER)
          setErrors({})
        }
        onOpenChange(next)
      }}
      title="Agregar al equipo"
      description="Crea una cuenta de administración o soporte. La persona recibe un correo para definir su contraseña."
      submitLabel="Agregar"
      what="el miembro"
      onSubmit={async () => {
        if (!validate()) return false
        const member = await getAdminConsoleService().addStaffMember({ ...values, email: values.email.trim(), name: values.name.trim(), surname: values.surname.trim() })
        toast.success(`Agregaste a ${member.name ?? member.email}. Le enviamos el correo para definir su contraseña.`)
        setValues(EMPTY_MEMBER)
        reload()
      }}
    >
      <TextField id="miembro-correo" label="Correo" type="email" autoComplete="off" value={values.email} error={errors.email} onChange={(event) => set('email', event.target.value)} required />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="miembro-nombre" label="Nombre" autoComplete="off" value={values.name} error={errors.name} onChange={(event) => set('name', event.target.value)} required />
        <TextField id="miembro-apellido" label="Apellido" autoComplete="off" value={values.surname} error={errors.surname} onChange={(event) => set('surname', event.target.value)} required />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="miembro-identificacion" label="Identificación" autoComplete="off" value={values.identification} error={errors.identification} onChange={(event) => set('identification', event.target.value)} required />
        <TextField id="miembro-nacimiento" label="Fecha de nacimiento" type="date" value={values.dateBirth} error={errors.dateBirth} onChange={(event) => set('dateBirth', event.target.value)} required />
      </div>
      <TextField id="miembro-telefono" label="Teléfono" type="tel" autoComplete="off" value={values.numberPhone} error={errors.numberPhone} onChange={(event) => set('numberPhone', event.target.value)} required />
      <SelectField
        id="miembro-rol"
        label="Rol"
        placeholder="Elige un rol"
        options={roles.map((role) => ({ value: role.id, label: role.name }))}
        value={values.roleId}
        error={errors.roleId}
        hint={roles.find((role) => role.id === values.roleId)?.description ?? undefined}
        onChange={(event) => set('roleId', event.target.value)}
      />
    </FormSheet>
  )
}

/** Cambio de rol de un miembro. Al guardar se cierran sus sesiones para que los permisos nuevos apliquen al ingresar. */
function RoleChangeSheet({ member, roles, onClose }: { member?: StaffMember; roles: StaffRole[]; onClose: () => void }) {
  const [roleId, setRoleId] = useState('')
  const current = roleId || member?.roleId || ''
  return (
    <FormSheet
      open={member !== undefined}
      onOpenChange={(open) => {
        if (!open) {
          setRoleId('')
          onClose()
        }
      }}
      title="Cambiar rol"
      description={`${member?.name ?? member?.email ?? ''}: al guardar se cierran sus sesiones y recibe los permisos nuevos al volver a ingresar.`}
      submitLabel="Guardar rol"
      what="el rol"
      onSubmit={async () => {
        if (!member || current === member.roleId) return
        await getAdminConsoleService().updateStaffMember(member.accountId, { roleId: current })
        toast.success(`Cambiaste el rol de ${member.name ?? member.email}.`)
        setRoleId('')
        reload()
      }}
    >
      <SelectField id="cambio-rol" label="Rol" options={roles.map((role) => ({ value: role.id, label: role.name }))} value={current} onChange={(event) => setRoleId(event.target.value)} />
      <RolePermissions role={roles.find((role) => role.id === current)} />
    </FormSheet>
  )
}

function RolePermissions({ role }: { role?: StaffRole }) {
  if (!role) return null
  return (
    <ul className="flex flex-wrap gap-1.5" aria-label={`Permisos de ${role.name}`}>
      {role.permissions.map((permission) => (
        <li key={permission}>
          <StatusBadge>{PERMISSION_LABEL[permission] ?? permission}</StatusBadge>
        </li>
      ))}
    </ul>
  )
}

/** Alta y edición de roles: nombre, descripción y permisos (casillas con la descripción del servidor). */
function RoleSheet({ open, role, permissions, onClose }: { open: boolean; role?: StaffRole; permissions: StaffPermission[]; onClose: () => void }) {
  const [name, setName] = useState(role?.name ?? '')
  const [description, setDescription] = useState(role?.description ?? '')
  const [selected, setSelected] = useState<PlatformPermission[]>(role?.permissions ?? [])
  const [error, setError] = useState<string>()
  return (
    <FormSheet
      open={open}
      onOpenChange={(next) => !next && onClose()}
      title={role ? `Editar ${role.name}` : 'Nuevo rol'}
      description={role?.system ? 'Rol predefinido: puedes cambiar su nombre y permisos, pero no eliminarlo.' : 'Elige qué secciones de la consola puede usar este rol.'}
      submitLabel={role ? 'Guardar rol' : 'Crear rol'}
      what="el rol"
      onSubmit={async () => {
        if (!name.trim()) {
          setError('Escribe el nombre del rol.')
          return false
        }
        if (selected.length === 0) {
          setError('Elige al menos un permiso.')
          return false
        }
        setError(undefined)
        const input = { name: name.trim(), description: description.trim() || undefined, permissions: selected }
        const service = getAdminConsoleService()
        if (role) await service.updateStaffRole(role.id, input)
        else await service.createStaffRole(input)
        toast.success(role ? `Guardaste el rol ${input.name}. Sus miembros reciben los permisos al volver a ingresar.` : `Creaste el rol ${input.name}.`)
        reload()
      }}
    >
      {error ? <FormAlert title="Revisa el rol">{error}</FormAlert> : null}
      <TextField id="rol-nombre" label="Nombre" maxLength={100} autoComplete="off" value={name} onChange={(event) => setName(event.target.value)} required />
      <TextAreaField id="rol-descripcion" label="Descripción" optional maxLength={255} rows={2} value={description} onChange={(event) => setDescription(event.target.value)} />
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-sm font-semibold text-ap-ink">Permisos</legend>
        {permissions.map((permission) => {
          const id = `permiso-${permission.code}`
          return (
            <label key={permission.code} htmlFor={id} className="flex min-h-11 items-start gap-3 rounded-md border border-ap-line px-3 py-2.5 text-sm hover:bg-ap-paper">
              <Checkbox
                id={id}
                checked={selected.includes(permission.code)}
                onCheckedChange={(checked) => setSelected((current) => (checked ? [...current, permission.code] : current.filter((code) => code !== permission.code)))}
              />
              <span className="flex flex-col">
                <span className="font-semibold text-ap-ink">{PERMISSION_LABEL[permission.code] ?? permission.code}</span>
                <span className="text-ap-muted">{permission.description}</span>
              </span>
            </label>
          )
        })}
      </fieldset>
    </FormSheet>
  )
}

type Pending = { kind: 'toggle' | 'remove'; member: StaffMember } | { kind: 'delete-role'; role: StaffRole }

/**
 * `/admin/equipo` (solo root): cuentas de administración y soporte técnico, sus roles y los permisos de cada rol.
 * Gestionar el equipo nunca se delega, para evitar escalada de privilegios.
 */
export default function AdminStaffPage() {
  const members = useAsync(`${KEY}:members`, () => getAdminConsoleService().staffMembers())
  const roles = useAsync(`${KEY}:roles`, () => getAdminConsoleService().staffRoles())
  const permissions = useAsync(`${KEY}:permissions`, () => getAdminConsoleService().staffPermissions())
  const [adding, setAdding] = useState(false)
  const [changingRole, setChangingRole] = useState<StaffMember>()
  const [roleSheet, setRoleSheet] = useState<{ role?: StaffRole } | undefined>()
  const [pending, setPending] = useState<Pending>()
  const roleList = roles.data ?? []

  const memberColumns: DataColumn<StaffMember>[] = [
    {
      id: 'miembro',
      header: 'Miembro',
      rowHeader: true,
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-semibold text-ap-ink">{row.name ?? row.email}</p>
          <p className="break-all text-sm text-ap-muted">{row.email}</p>
        </div>
      ),
    },
    { id: 'rol', header: 'Rol', cell: (row) => <span className="text-ap-ink">{row.roleName}</span> },
    { id: 'estado', header: 'Estado', cell: (row) => (row.active ? <StatusBadge tone="success">Activo</StatusBadge> : <StatusBadge>Desactivado</StatusBadge>) },
    { id: 'ingreso', header: 'Último ingreso', className: 'hidden md:table-cell', cell: (row) => <span className="ap-ref text-ap-ink">{row.lastLoginAt ? formatDateTime(row.lastLoginAt) : 'Nunca'}</span> },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <RowActions
          label={`Acciones para ${row.email}`}
          actions={[
            { label: 'Cambiar rol', icon: <PencilIcon aria-hidden />, onSelect: () => setChangingRole(row) },
            { label: row.active ? 'Desactivar' : 'Activar', icon: <PowerIcon aria-hidden />, onSelect: () => setPending({ kind: 'toggle', member: row }), destructive: row.active },
            { label: 'Quitar del equipo', icon: <UserMinusIcon aria-hidden />, onSelect: () => setPending({ kind: 'remove', member: row }), destructive: true },
          ]}
        />
      ),
    },
  ]

  async function confirm() {
    if (!pending) return
    const service = getAdminConsoleService()
    if (pending.kind === 'toggle') {
      await service.updateStaffMember(pending.member.accountId, { active: !pending.member.active })
      toast.success(pending.member.active ? 'Desactivaste al miembro y cerraste sus sesiones.' : 'Activaste al miembro.')
    } else if (pending.kind === 'remove') {
      await service.removeStaffMember(pending.member.accountId)
      toast.success('Quitaste al miembro del equipo y desactivaste su cuenta.')
    } else if (pending.kind === 'delete-role') {
      await service.deleteStaffRole(pending.role.id)
      toast.success(`Eliminaste el rol ${pending.role.name}.`)
    }
    setPending(undefined)
    reload()
  }

  const confirmText =
    pending?.kind === 'toggle'
      ? pending.member.active
        ? { title: 'Desactivar al miembro', description: 'No podrá ingresar a la consola y se cerrarán sus sesiones.', label: 'Desactivar' }
        : { title: 'Activar al miembro', description: 'Podrá volver a ingresar con los permisos de su rol.', label: 'Activar' }
      : pending?.kind === 'remove'
        ? { title: 'Quitar del equipo', description: 'Se desactiva su cuenta y deja de tener acceso. Queda registrado en la auditoría.', label: 'Quitar' }
        : pending?.kind === 'delete-role'
          ? { title: `Eliminar el rol ${pending.role.name}`, description: 'El rol no tiene miembros. Esta acción no se puede deshacer.', label: 'Eliminar' }
          : undefined

  return (
    <AdminPage
      title="Equipo"
      description="Cuentas de administración y soporte técnico de la plataforma, con sus roles y permisos. Solo root las gestiona."
      actions={
        <Button className="h-11 px-4" onClick={() => setAdding(true)} disabled={roleList.length === 0}>
          <PlusIcon aria-hidden />
          Agregar miembro
        </Button>
      }
    >
      <div className="flex flex-col gap-10">
        <AdminSection id="equipo-miembros" title="Miembros" meta="Los permisos viajan en la sesión: un cambio aplica al volver a ingresar">
          <AdminBoundary result={members} what="el equipo" skeleton={<ListSkeleton rows={4} label="Cargando el equipo" />}>
            {(rows) =>
              rows.length === 0 ? (
                <EmptyState icon={<UserCogIcon />} title="Aún no hay miembros en el equipo" description="Agrega cuentas de administración o soporte técnico y asígnales un rol con los permisos que necesitan.">
                  <Button className="h-11 px-4" onClick={() => setAdding(true)} disabled={roleList.length === 0}>
                    <PlusIcon aria-hidden />
                    Agregar miembro
                  </Button>
                </EmptyState>
              ) : (
                <DataTable caption="Miembros del equipo de la plataforma" columns={memberColumns} rows={rows} getRowId={(row) => row.accountId} />
              )
            }
          </AdminBoundary>
        </AdminSection>

        <AdminSection
          id="equipo-roles"
          title="Roles y permisos"
          meta="Cada rol habilita secciones de la consola"
          actions={
            <Button variant="outline" className="h-11 px-4" onClick={() => setRoleSheet({})} disabled={permissions.data === undefined}>
              <PlusIcon aria-hidden />
              Nuevo rol
            </Button>
          }
        >
          <AdminBoundary result={roles} what="los roles" skeleton={<ListSkeleton rows={4} label="Cargando los roles" />}>
            {(rows) => (
              <ul className="divide-y divide-ap-line border-y border-ap-line">
                {rows.map((role) => (
                  <li key={role.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-start sm:justify-between">
                    <div className="flex min-w-0 flex-col gap-1.5">
                      <p className="flex flex-wrap items-center gap-2 font-semibold text-ap-ink">
                        {role.name}
                        {role.system ? <StatusBadge tone="info">Predefinido</StatusBadge> : null}
                        <span className="ap-ref font-normal text-ap-muted">
                          {role.members} {role.members === 1 ? 'miembro' : 'miembros'}
                        </span>
                      </p>
                      {role.description ? <p className="text-sm text-ap-muted">{role.description}</p> : null}
                      <RolePermissions role={role} />
                    </div>
                    <RowActions
                      label={`Acciones para el rol ${role.name}`}
                      actions={[
                        { label: 'Editar', icon: <PencilIcon aria-hidden />, onSelect: () => setRoleSheet({ role }) },
                        ...(role.system || role.members > 0 ? [] : [{ label: 'Eliminar', icon: <Trash2Icon aria-hidden />, onSelect: () => setPending({ kind: 'delete-role', role }), destructive: true }]),
                      ]}
                    />
                  </li>
                ))}
              </ul>
            )}
          </AdminBoundary>
        </AdminSection>
      </div>

      <MemberSheet open={adding} onOpenChange={setAdding} roles={roleList} />
      <RoleChangeSheet member={changingRole} roles={roleList} onClose={() => setChangingRole(undefined)} />
      {roleSheet ? (
        <RoleSheet key={roleSheet.role?.id ?? 'nuevo'} open role={roleSheet.role} permissions={permissions.data ?? []} onClose={() => setRoleSheet(undefined)} />
      ) : null}
      {pending && confirmText ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setPending(undefined)}
          title={confirmText.title}
          description={confirmText.description}
          confirmLabel={confirmText.label}
          destructive={!(pending.kind === 'toggle' && !pending.member.active)}
          onConfirm={confirm}
        />
      ) : null}
    </AdminPage>
  )
}
