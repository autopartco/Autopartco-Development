import { useEffect, useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { DiscardChangesDialog } from '@/components/common/discard-changes-dialog'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { ApiError } from '@/lib/api'
import { useCloseGuard } from '@/lib/use-close-guard'
import { useDraftForm, type FieldErrors } from '@/lib/use-draft-form'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import { useCities } from '@/features/location/hooks'
import { getBranchService } from '../api/branch-service'
import type { Branch, BranchInput } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { focusById, PHONE_PATTERN } from '../lib/forms'

const FIELDS = ['name', 'numberPhone', 'addressLine', 'addressComplement', 'city', 'department'] as const
type Field = (typeof FIELDS)[number]
const fieldId = (field: string) => `sucursal-${field}`

interface Values {
  name: string
  numberPhone: string
  addressLine: string
  addressComplement: string
  city: string
  department: string
}

function valuesOf(branch: Branch | undefined): Values {
  return {
    name: branch?.name ?? '',
    numberPhone: branch?.numberPhone ?? '',
    addressLine: branch?.location?.addressLine ?? '',
    addressComplement: branch?.location?.addressComplement ?? '',
    city: branch?.location?.city ?? '',
    department: branch?.location?.department ?? '',
  }
}

function validate(values: Values): FieldErrors {
  const errors: FieldErrors = {}
  if (!values.name.trim()) errors.name = 'Escribe el nombre de la sucursal.'
  else if (values.name.trim().length > 255) errors.name = 'El nombre no puede superar 255 caracteres.'
  if (!values.numberPhone.trim()) errors.numberPhone = 'Escribe el teléfono de la sucursal.'
  else if (!PHONE_PATTERN.test(values.numberPhone.trim())) errors.numberPhone = 'El teléfono debe tener entre 7 y 10 dígitos, sin espacios ni guiones.'
  if (!values.addressLine.trim()) errors.addressLine = 'Escribe la dirección.'
  else if (values.addressLine.trim().length > 255) errors.addressLine = 'La dirección no puede superar 255 caracteres.'
  if (values.addressComplement.trim().length > 255) errors.addressComplement = 'No puede superar 255 caracteres.'
  if (!values.city.trim()) errors.city = 'Escribe la ciudad.'
  if (!values.department.trim()) errors.department = 'Escribe el departamento.'
  return errors
}

interface BranchFormProps {
  branch: Branch | undefined
  /** Cierra sin preguntar (tras guardar). */
  onDone: () => void
  /** «Cancelar»: cierra, o pregunta si hay cambios. */
  onCancel: () => void
  /** Avisa al panel si hay cambios sin enviar. */
  onDirtyChange: (dirty: boolean) => void
}

function BranchForm({ branch, onDone, onCancel, onDirtyChange }: BranchFormProps) {
  const { storeId, promptUpgrade, invalidateStore } = usePortal()
  const cities = useCities()
  const draft = useDraftForm<Values>({ initial: valuesOf(branch), validate })
  const { values } = draft
  const submit = useSubmitOnce(async (input: BranchInput) => {
    const service = await getBranchService()
    if (branch) await service.update(storeId, branch.id, input)
    else await service.create(storeId, input)
  })
  const pending = submit.pending
  const blocker = useUnsavedChanges(draft.isDirty && !pending)
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    onDirtyChange(draft.isDirty && !pending)
    return () => onDirtyChange(false)
  }, [draft.isDirty, pending, onDirtyChange])

  const bind = (field: Field) => ({
    id: fieldId(field),
    value: values[field],
    error: draft.errorFor(field),
    onChange: (event: { target: { value: string } }) => draft.set(field, event.target.value),
    onBlur: () => draft.touch(field),
  })

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(undefined)
    const check = draft.validateAll()
    if (!check.valid) {
      focusById(check.firstInvalid ? fieldId(check.firstInvalid) : undefined)
      return
    }
    const input: BranchInput = {
      name: values.name.trim(),
      numberPhone: values.numberPhone.trim(),
      location: {
        addressLine: values.addressLine.trim(),
        addressComplement: values.addressComplement.trim() || undefined,
        city: values.city.trim(),
        department: values.department.trim(),
        country: 'Colombia',
      },
    }
    // ÚNICA escritura: crear/actualizar al pulsar el botón final.
    const result = await submit.run(input)
    if (result.status === 'ok') {
      draft.markSaved()
      onDirtyChange(false)
      invalidateStore()
      toast.success(branch ? 'Sucursal actualizada.' : 'Sucursal creada.')
      onDone()
    } else if (result.status === 'error') {
      const error = result.error
      if (promptUpgrade(error)) return
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        // El servidor nombra los campos de ubicación como `location.city`.
        const mapped: FieldErrors = {}
        for (const [key, message] of Object.entries(error.fieldErrors)) mapped[key.replace(/^location\./, '')] = message
        draft.setServerErrors(mapped)
        const first = FIELDS.find((field) => mapped[field])
        if (first) requestAnimationFrame(() => focusById(fieldId(first)))
      }
      setFormError(actionErrorMessage(error, 'No pudimos guardar la sucursal. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex min-h-0 flex-1 flex-col" aria-busy={pending || undefined}>
      <div className="flex flex-1 flex-col gap-4 overflow-y-auto px-4 pb-4">
        {formError ? <FormAlert ref={alertRef} title="No se guardó la sucursal">{formError}</FormAlert> : null}
        <TextField {...bind('name')} label="Nombre" maxLength={255} autoComplete="off" required hint="Ej.: Sede Calle 30." />
        <TextField {...bind('numberPhone')} label="Teléfono" inputMode="tel" maxLength={10} autoComplete="off" required hint="Fijo o celular, de 7 a 10 dígitos." />
        <TextField {...bind('addressLine')} label="Dirección" maxLength={255} autoComplete="off" required />
        <TextField {...bind('addressComplement')} label="Complemento" optional maxLength={255} autoComplete="off" hint="Local, bodega, barrio." />
        <datalist id="sucursal-ciudades">
          {(cities.data ?? []).map((city) => (
            <option key={city.id} value={city.name} />
          ))}
        </datalist>
        <TextField {...bind('city')} label="Ciudad" list="sucursal-ciudades" autoComplete="off" required />
        <TextField {...bind('department')} label="Departamento" autoComplete="off" required />
      </div>
      <div className="flex flex-col-reverse gap-2 border-t border-border p-4 sm:flex-row sm:justify-end">
        <Button type="button" variant="outline" className="h-11 px-5" onClick={onCancel} disabled={pending}>
          Cancelar
        </Button>
        <Button type="submit" className="h-11 px-5" disabled={pending}>
          {pending ? 'Guardando…' : branch ? 'Guardar cambios' : 'Crear sucursal'}
        </Button>
      </div>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}

/** Alta y edición de una sucursal en un panel lateral (`Sheet`). */
export function BranchSheet({ open, onOpenChange, branch }: { open: boolean; onOpenChange: (open: boolean) => void; branch: Branch | undefined }) {
  const guard = useCloseGuard(() => onOpenChange(false))
  return (
    <Sheet open={open} onOpenChange={(next) => (next ? onOpenChange(true) : guard.requestClose())}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-md">
        <SheetHeader className="p-4 pr-16">
          <SheetTitle className="text-lg font-extrabold tracking-tight">{branch ? 'Editar sucursal' : 'Nueva sucursal'}</SheetTitle>
          <SheetDescription>
            {branch ? 'Cambia los datos de contacto y la dirección.' : 'Una sucursal es un punto desde el que despachas pedidos.'}
          </SheetDescription>
        </SheetHeader>
        {/* key: cada apertura (o cambio de sucursal) parte de un formulario limpio. */}
        <BranchForm key={branch?.id ?? 'nueva'} branch={branch} onDone={() => onOpenChange(false)} onCancel={guard.requestClose} onDirtyChange={guard.setDirty} />
        <DiscardChangesDialog guard={guard} />
      </SheetContent>
    </Sheet>
  )
}
