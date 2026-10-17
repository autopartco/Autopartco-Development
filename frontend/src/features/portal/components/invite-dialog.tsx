import { useEffect, useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { DiscardChangesDialog } from '@/components/common/discard-changes-dialog'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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
import { PERMISSION_LABEL } from '../lib/labels'
import { QueryError } from './query-boundary'

interface InviteValues {
  email: string
  roleName: string
  permissionCodes: string[]
}

function validate(values: InviteValues): FieldErrors {
  const errors: FieldErrors = {}
  if (!values.email.trim()) errors.email = 'Escribe el correo de la persona.'
  else if (!EMAIL_PATTERN.test(values.email.trim())) errors.email = 'El correo debe tener un formato válido, por ejemplo nombre@correo.com.'
  if (!values.roleName.trim()) errors.roleName = 'Escribe el nombre del rol, por ejemplo “Vendedor”.'
  else if (values.roleName.trim().length > 100) errors.roleName = 'Máximo 100 caracteres.'
  if (values.permissionCodes.length === 0) errors.permissionCodes = 'Elige al menos un permiso.'
  return errors
}

const FIELD_IDS: Record<string, string> = { email: 'invitar-email', roleName: 'invitar-rol', permissionCodes: 'invitar-permisos' }

interface InviteFormProps {
  /** Cierra sin preguntar (tras enviar). */
  onDone: () => void
  /** «Cancelar»: cierra, o pregunta si hay cambios. */
  onCancel: () => void
  onDirtyChange: (dirty: boolean) => void
}

function InviteForm({ onDone, onCancel, onDirtyChange }: InviteFormProps) {
  const { storeId, promptUpgrade, invalidateStore } = usePortal()
  // Lectura de apoyo: los permisos disponibles se consultan al abrir.
  const options = useAsync(`portal:${storeId}:permission-options`, async () => (await getStaffService()).permissionOptions(storeId))
  const draft = useDraftForm<InviteValues>({ initial: { email: '', roleName: '', permissionCodes: [] }, validate })
  const { email, roleName, permissionCodes: codes } = draft.values
  const submit = useSubmitOnce(async (input: { email: string; roleName: string; permissionCodes: string[] }) => (await getStaffService()).invite(storeId, input))
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
    const result = await submit.run({ email: email.trim(), roleName: roleName.trim(), permissionCodes: codes })
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
        <DialogDescription>Le enviaremos un correo con un enlace para unirse al equipo. Vence en 7 días.</DialogDescription>
      </DialogHeader>
      {formError ? <FormAlert ref={alertRef} title="No se envió la invitación">{formError}</FormAlert> : null}
      <TextField id="invitar-email" type="email" label="Correo" value={email} onChange={(event) => draft.set('email', event.target.value)} onBlur={() => draft.touch('email')} error={shown('email')} autoComplete="off" inputMode="email" maxLength={100} required />
      <TextField id="invitar-rol" label="Nombre del rol" value={roleName} onChange={(event) => draft.set('roleName', event.target.value)} onBlur={() => draft.touch('roleName')} error={shown('roleName')} autoComplete="off" maxLength={100} required hint="Cómo se llamará su cargo en el almacén. Ej.: Vendedor, Bodeguero." />

      {options.status === 'error' ? (
        <QueryError error={options.error} what="los permisos disponibles" onRetry={options.retry} />
      ) : (
        <fieldset id="invitar-permisos" tabIndex={-1} className="flex flex-col gap-1 outline-none" aria-describedby={shown('permissionCodes') ? 'invitar-permisos-error' : undefined}>
          <legend className="mb-1.5 text-sm font-semibold">Permisos</legend>
          {options.data === undefined ? (
            <p className="text-sm text-muted-foreground" role="status">
              Cargando permisos…
            </p>
          ) : (
            options.data.map((option) => (
              <label key={option.code} className="flex min-h-10 cursor-pointer items-center gap-3 rounded-md px-1 text-sm hover:bg-concrete pointer-coarse:min-h-11">
                <Checkbox
                  checked={codes.includes(option.code)}
                  onCheckedChange={(checked) => draft.set('permissionCodes', checked === true ? [...codes, option.code] : codes.filter((code) => code !== option.code))}
                />
                <span>{option.description || PERMISSION_LABEL[option.code as keyof typeof PERMISSION_LABEL] || option.code}</span>
              </label>
            ))
          )}
          {shown('permissionCodes') ? (
            <p id="invitar-permisos-error" className="mt-1 text-sm font-medium text-destructive">
              {shown('permissionCodes')}
            </p>
          ) : null}
        </fieldset>
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

/** Invitación de un colaborador. Los límites de plan (`USERS`, `CUSTOM_ROLES`) abren el diálogo de mejora. */
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
