import { useState } from 'react'
import { toast } from 'sonner'
import { FormAlert } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getStaffService } from '../api/staff-service'
import type { StaffMember, StaffRole, StaffRoleCode } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { RolePicker } from './role-picker'

function ChangeRoleForm({ member, roles, onDone }: { member: StaffMember; roles: readonly StaffRole[]; onDone: () => void }) {
  const { storeId, invalidateStore } = usePortal()
  const [roleCode, setRoleCode] = useState<StaffRoleCode | undefined>(member.roleCode ?? undefined)
  const [error, setError] = useState<string>()
  const submit = useSubmitOnce(async (code: StaffRoleCode) => (await getStaffService()).changeRole(storeId, member.id, code))
  const unchanged = roleCode === undefined || roleCode === member.roleCode
  const fullName = `${member.name} ${member.surname}`.trim()

  async function save() {
    if (!roleCode) return
    setError(undefined)
    const result = await submit.run(roleCode)
    if (result.status === 'ok') {
      invalidateStore()
      toast.success(`${fullName} ahora tiene el rol ${roles.find((role) => role.code === roleCode)?.name ?? roleCode}.`)
      onDone()
    } else if (result.status === 'error') {
      setError(actionErrorMessage(result.error, 'No pudimos cambiar el rol. Inténtalo de nuevo.'))
    }
  }

  return (
    <div className="flex flex-col gap-4" aria-busy={submit.pending || undefined}>
      <DialogHeader>
        <DialogTitle>Cambiar rol de {fullName}</DialogTitle>
        <DialogDescription>El cambio aplica de inmediato en su próxima acción en el portal.</DialogDescription>
      </DialogHeader>
      {error ? <FormAlert title="No se cambió el rol">{error}</FormAlert> : null}
      <RolePicker id="cambiar-rol" roles={roles} value={roleCode} onChange={setRoleCode} disabled={submit.pending} />
      <DialogFooter>
        <Button type="button" variant="outline" className="h-11 px-4" disabled={submit.pending} onClick={onDone}>
          Cancelar
        </Button>
        <Button type="button" className="h-11 px-4" disabled={submit.pending || unchanged} onClick={() => void save()}>
          {submit.pending ? 'Guardando…' : 'Guardar rol'}
        </Button>
      </DialogFooter>
    </div>
  )
}

/** Cambia el rol predefinido de un colaborador (`PATCH /staff/members/{id}/role`). */
export function ChangeRoleDialog({ member, roles, onClose }: { member: StaffMember | undefined; roles: readonly StaffRole[]; onClose: () => void }) {
  // Conserva el último colaborador mientras dura la animación de cierre, para no mostrar el diálogo vacío.
  const [shown, setShown] = useState(member)
  if (member !== undefined && member !== shown) setShown(member)
  return (
    <Dialog open={member !== undefined} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>{shown ? <ChangeRoleForm key={`${shown.id}:${shown.roleCode ?? ''}:${member === undefined ? 'closed' : 'open'}`} member={shown} roles={roles} onDone={onClose} /> : null}</DialogContent>
    </Dialog>
  )
}
