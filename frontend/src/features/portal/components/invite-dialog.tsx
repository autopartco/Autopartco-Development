import { useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError } from '@/lib/api'
import { useAsync } from '@/lib/use-async'
import { getStaffService } from '../api/staff-service'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { EMAIL_PATTERN, focusById } from '../lib/forms'
import { PERMISSION_LABEL } from '../lib/labels'
import { QueryError } from './query-boundary'

function InviteForm({ onDone }: { onDone: () => void }) {
  const { storeId, promptUpgrade, invalidateStore } = usePortal()
  const options = useAsync(`portal:${storeId}:permission-options`, async () => (await getStaffService()).permissionOptions(storeId))
  const [email, setEmail] = useState('')
  const [roleName, setRoleName] = useState('')
  const [codes, setCodes] = useState<string[]>([])
  const [submitted, setSubmitted] = useState(false)
  const [pending, setPending] = useState(false)
  const [formError, setFormError] = useState<string>()
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const alertRef = useRef<HTMLDivElement>(null)

  const errors = {
    email: !email.trim() ? 'Escribe el correo de la persona.' : !EMAIL_PATTERN.test(email.trim()) ? 'El correo debe tener un formato válido, por ejemplo nombre@correo.com.' : undefined,
    roleName: !roleName.trim() ? 'Escribe el nombre del rol, por ejemplo “Vendedor”.' : roleName.trim().length > 100 ? 'Máximo 100 caracteres.' : undefined,
    permissionCodes: codes.length === 0 ? 'Elige al menos un permiso.' : undefined,
  }
  const shown = (field: keyof typeof errors) => serverErrors[field] ?? (submitted ? errors[field] : undefined)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    setSubmitted(true)
    setFormError(undefined)
    if (errors.email || errors.roleName || errors.permissionCodes) {
      focusById(errors.email ? 'invitar-email' : errors.roleName ? 'invitar-rol' : 'invitar-permisos')
      return
    }
    setPending(true)
    try {
      await (await getStaffService()).invite(storeId, { email: email.trim(), roleName: roleName.trim(), permissionCodes: codes })
      invalidateStore()
      toast.success(`Enviamos la invitación a ${email.trim()}.`)
      onDone()
    } catch (error) {
      if (promptUpgrade(error)) {
        onDone()
        return
      }
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) setServerErrors(error.fieldErrors)
      setFormError(actionErrorMessage(error, 'No pudimos enviar la invitación. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    } finally {
      setPending(false)
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-4" aria-busy={pending || undefined}>
      <DialogHeader>
        <DialogTitle>Invitar a una persona</DialogTitle>
        <DialogDescription>Le enviaremos un correo con un enlace para unirse al equipo. Vence en 7 días.</DialogDescription>
      </DialogHeader>
      {formError ? <FormAlert ref={alertRef} title="No se envió la invitación">{formError}</FormAlert> : null}
      <TextField id="invitar-email" type="email" label="Correo" value={email} onChange={(event) => setEmail(event.target.value)} error={shown('email')} autoComplete="off" inputMode="email" maxLength={100} required />
      <TextField id="invitar-rol" label="Nombre del rol" value={roleName} onChange={(event) => setRoleName(event.target.value)} error={shown('roleName')} autoComplete="off" maxLength={100} required hint="Cómo se llamará su cargo en el almacén. Ej.: Vendedor, Bodeguero." />

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
                  onCheckedChange={(checked) => setCodes((current) => (checked === true ? [...current, option.code] : current.filter((code) => code !== option.code)))}
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
        <DialogClose render={<Button type="button" variant="outline" className="h-11 px-4" disabled={pending} />}>Cancelar</DialogClose>
        <Button type="submit" className="h-11 px-4" disabled={pending}>
          {pending ? 'Enviando…' : 'Enviar invitación'}
        </Button>
      </DialogFooter>
    </form>
  )
}

/** Invitación de un colaborador. Los límites de plan (`USERS`, `CUSTOM_ROLES`) abren el diálogo de mejora. */
export function InviteDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <InviteForm onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  )
}
