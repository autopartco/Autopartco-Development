import { useEffect, useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { DiscardChangesDialog } from '@/components/common/discard-changes-dialog'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError } from '@/lib/api'
import { useAsync } from '@/lib/use-async'
import { useCloseGuard } from '@/lib/use-close-guard'
import { useDraftForm, type FieldErrors } from '@/lib/use-draft-form'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import { getStaffService } from '../api/staff-service'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { EMAIL_PATTERN, focusById } from '../lib/forms'
import type { StaffRoleCode } from '../api/types'
import { QueryError } from './query-boundary'
import { RolePicker } from './role-picker'

interface InviteValues {
  email: string
  roleCode: StaffRoleCode | undefined
}

function validate(values: InviteValues): FieldErrors {
  const errors: FieldErrors = {}
  if (!values.email.trim()) errors.email = 'Escribe el correo de la persona.'
  else if (!EMAIL_PATTERN.test(values.email.trim())) errors.email = 'El correo debe tener un formato válido, por ejemplo nombre@correo.com.'
  if (!values.roleCode) errors.roleCode = 'Elige el rol que tendrá en el almacén.'
  return errors
}

const FIELD_IDS: Record<string, string> = { email: 'invitar-email', roleCode: 'invitar-rol' }

interface InviteFormProps {
  /** Cierra sin preguntar (tras enviar). */
  onDone: () => void
  /** «Cancelar»: cierra, o pregunta si hay cambios. */
  onCancel: () => void
  onDirtyChange: (dirty: boolean) => void
}

function InviteForm({ onDone, onCancel, onDirtyChange }: InviteFormProps) {
  const { storeId, promptUpgrade, invalidateStore } = usePortal()
  // Lectura de apoyo: los roles disponibles se consultan al abrir.
  const roles = useAsync(`portal:${storeId}:staff-roles`, async () => (await getStaffService()).roles(storeId))
  const draft = useDraftForm<InviteValues>({ initial: { email: '', roleCode: undefined }, validate })
  const { email, roleCode } = draft.values
  const submit = useSubmitOnce(async (input: { email: string; roleCode: StaffRoleCode }) => (await getStaffService()).invite(storeId, input))
  const pending = submit.pending
  const blocker = useUnsavedChanges(draft.isDirty && !pending)
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    onDirtyChange(draft.isDirty && !pending)
    return () => onDirtyChange(false)
  }, [draft.isDirty, pending, onDirtyChange])

  const shown = (field: keyof InviteValues) => draft.errorFor(field)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(undefined)
    const check = draft.validateAll()
    if (!check.valid) {
      focusById(check.firstInvalid ? FIELD_IDS[check.firstInvalid] : undefined)
      return
    }
    // ÚNICA escritura: la invitación se crea al pulsar «Enviar invitación».
    if (!roleCode) return
    const result = await submit.run({ email: email.trim(), roleCode })
    if (result.status === 'ok') {
      draft.markSaved()
      onDirtyChange(false)
      invalidateStore()
      toast.success(`Enviamos la invitación a ${email.trim()}.`)
      onDone()
    } else if (result.status === 'error') {
      const error = result.error
      if (promptUpgrade(error)) {
        onDirtyChange(false)
        onDone()
        return
      }
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) draft.setServerErrors(error.fieldErrors)
      setFormError(actionErrorMessage(error, 'No pudimos enviar la invitación. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-4" aria-busy={pending || undefined}>
      <DialogHeader>
        <DialogTitle>Invitar a una persona</DialogTitle>
        <DialogDescription>Le enviaremos un correo con un enlace para unirse al equipo. Vence en 7 días y podrás cambiarle el rol cuando quieras.</DialogDescription>
      </DialogHeader>
      {formError ? <FormAlert ref={alertRef} title="No se envió la invitación">{formError}</FormAlert> : null}
      <TextField id="invitar-email" type="email" label="Correo" value={email} onChange={(event) => draft.set('email', event.target.value)} onBlur={() => draft.touch('email')} error={shown('email')} autoComplete="off" inputMode="email" maxLength={100} required />
      {roles.status === 'error' ? (
        <QueryError error={roles.error} what="los roles disponibles" onRetry={roles.retry} />
      ) : roles.data === undefined ? (
        <p className="text-sm text-ap-muted" role="status">
          Cargando roles…
        </p>
      ) : (
        <RolePicker id="invitar-rol" roles={roles.data} value={roleCode} onChange={(code) => draft.set('roleCode', code)} disabled={pending} error={shown('roleCode')} />
      )}

      <DialogFooter>
        <Button type="button" variant="outline" className="h-11 px-4" disabled={pending} onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" className="h-11 px-4" disabled={pending}>
          {pending ? 'Enviando…' : 'Enviar invitación'}
        </Button>
      </DialogFooter>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}

/** Invitación de un colaborador con un rol predefinido. El límite de usuarios del plan abre el diálogo de mejora. */
export function InviteDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const guard = useCloseGuard(() => onOpenChange(false))
  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(true) : guard.requestClose())}>
      <DialogContent>
        {/* Se monta al abrir: cada invitación parte de un formulario limpio. */}
        {open ? <InviteForm onDone={() => onOpenChange(false)} onCancel={guard.requestClose} onDirtyChange={guard.setDirty} /> : null}
        <DiscardChangesDialog guard={guard} />
      </DialogContent>
    </Dialog>
  )
}
