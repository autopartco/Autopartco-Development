import { useEffect, useId, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ImageIcon, TriangleAlertIcon } from 'lucide-react'
import { toast } from 'sonner'
import { DiscardChangesDialog } from '@/components/common/discard-changes-dialog'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Switch } from '@/components/ui/switch'
import type { ServiceCategory } from '@/features/marketplace/api/types'
import { SERVICE_CATEGORY_CODES, serviceCategoryLabel } from '@/features/services/lib/categories'
import { hasWeeklyHours } from '@/features/services/lib/opening-hours'
import { ApiError } from '@/lib/api'
import { useCloseGuard } from '@/lib/use-close-guard'
import { useDraftForm, type FieldErrors } from '@/lib/use-draft-form'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import {
  getStoreServicesService,
  SERVICE_IMAGE_MAX_BYTES,
  SERVICE_IMAGE_TYPES,
  SLOT_MINUTES_OPTIONS,
  type StoreServiceInput,
  type StoreServiceItem,
} from '../api/store-services-service'
import type { Branch } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { focusById, parseAmount, parseCount } from '../lib/forms'
import { SelectField, TextAreaField } from './form-controls'
import { ConfirmDialog } from './confirm-dialog'

const FIELDS = ['category', 'name', 'description', 'priceFrom', 'durationMinutes', 'branchIds', 'slotMinutes'] as const
const fieldId = (field: string) => `servicio-${field}`

interface Values {
  category: ServiceCategory
  name: string
  description: string
  priceFrom: string
  durationMinutes: string
  branchIds: string[]
  bookingEnabled: boolean
  slotMinutes: string
}

function valuesOf(service: StoreServiceItem | undefined): Values {
  return {
    category: service?.category ?? 'MECHANICS',
    name: service?.name ?? '',
    description: service?.description ?? '',
    priceFrom: service?.priceFrom != null ? String(service.priceFrom).replace('.', ',') : '',
    durationMinutes: service?.durationMinutes != null ? String(service.durationMinutes) : '',
    branchIds: service?.branchIds ?? [],
    bookingEnabled: service?.bookingEnabled ?? true,
    slotMinutes: String(service?.slotMinutes ?? 60),
  }
}

function validate(values: Values): FieldErrors {
  const errors: FieldErrors = {}
  if (!values.name.trim()) errors.name = 'Escribe el nombre del servicio.'
  else if (values.name.trim().length > 120) errors.name = 'El nombre no puede superar 120 caracteres.'
  else if (/[<>]/.test(values.name)) errors.name = 'El nombre no puede contener < ni >.'
  if (values.description.length > 1000) errors.description = 'La descripción no puede superar 1000 caracteres.'
  else if (/[<>]/.test(values.description)) errors.description = 'La descripción no puede contener < ni >.'
  if (values.priceFrom.trim()) {
    const price = parseAmount(values.priceFrom)
    if (price === undefined || price < 0) errors.priceFrom = 'Escribe un precio en pesos, por ejemplo 60.000.'
  }
  if (values.durationMinutes.trim()) {
    const minutes = parseCount(values.durationMinutes)
    if (minutes === undefined || minutes < 5 || minutes > 10080) errors.durationMinutes = 'La duración va de 5 a 10.080 minutos.'
  }
  if (values.bookingEnabled && values.branchIds.length === 0) errors.branchIds = 'Elige al menos una sucursal para recibir citas.'
  return errors
}

function buildInput(values: Values, service: StoreServiceItem | undefined): StoreServiceInput {
  return {
    category: values.category,
    name: values.name.trim(),
    description: values.description.trim() || null,
    priceFrom: values.priceFrom.trim() ? (parseAmount(values.priceFrom) ?? null) : null,
    durationMinutes: values.durationMinutes.trim() ? (parseCount(values.durationMinutes) ?? null) : null,
    branchIds: values.branchIds,
    bookingEnabled: values.bookingEnabled,
    slotMinutes: Number(values.slotMinutes),
    ...(service ? { version: service.version, sortOrder: service.sortOrder, active: service.active } : {}),
  }
}

/** Imagen del servicio: se guarda al elegir el archivo (no depende de «Guardar cambios»). */
function ServiceImage({ service, onChanged }: { service: StoreServiceItem; onChanged: (service: StoreServiceItem) => void }) {
  const { storeId, invalidateStore } = usePortal()
  const inputId = useId()
  const hintId = `${inputId}-ayuda`
  const input = useRef<HTMLInputElement>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string>()
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [broken, setBroken] = useState<string | null>(null)

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (!SERVICE_IMAGE_TYPES.includes(file.type)) return setError('Solo se permiten imágenes PNG, JPEG o WebP.')
    if (file.size > SERVICE_IMAGE_MAX_BYTES) return setError('La imagen no puede superar 5 MB.')
    setError(undefined)
    setPending(true)
    try {
      onChanged(await (await getStoreServicesService()).uploadImage(storeId, service.id, file))
      invalidateStore()
      toast.success('Guardamos la imagen del servicio.')
    } catch (cause) {
      setError(actionErrorMessage(cause, 'No pudimos subir la imagen. Inténtalo de nuevo.'))
    } finally {
      setPending(false)
    }
  }

  const shown = service.imageUrl && broken !== service.imageUrl ? service.imageUrl : undefined
  return (
    <div className="flex flex-col gap-2">
      <p className="text-sm font-semibold text-ap-ink">Imagen</p>
      <div className="flex items-center gap-4">
        <div className="grid size-24 shrink-0 place-items-center overflow-hidden rounded-lg border border-ap-line bg-ap-paper text-ap-muted">
          {shown ? (
            <img src={shown} alt={`Imagen de ${service.name}`} className="size-full object-cover" onError={() => setBroken(service.imageUrl)} />
          ) : (
            <span className="flex flex-col items-center gap-1 px-2 text-center text-xs">
              <ImageIcon className="size-5" aria-hidden />
              Sin imagen
            </span>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          <input ref={input} id={inputId} type="file" accept={SERVICE_IMAGE_TYPES.join(',')} className="sr-only" aria-describedby={hintId} onChange={(event) => void upload(event)} disabled={pending} />
          <Button type="button" variant="outline" className="h-11 px-4" disabled={pending} onClick={() => input.current?.click()}>
            {pending ? 'Subiendo…' : service.imageUrl ? 'Cambiar imagen' : 'Subir imagen'}
          </Button>
          {service.imageUrl ? (
            <Button type="button" variant="ghost" className="h-11 px-3" disabled={pending} onClick={() => setConfirmRemove(true)}>
              Quitar
            </Button>
          ) : null}
        </div>
      </div>
      {error ? (
        <p role="alert" className="text-sm font-medium text-ap-danger">
          {error}
        </p>
      ) : null}
      <p id={hintId} className="text-xs text-ap-muted">
        PNG, JPEG o WebP de hasta 5 MB. Se guarda al elegir el archivo.
      </p>
      <ConfirmDialog
        open={confirmRemove}
        onOpenChange={setConfirmRemove}
        title="¿Quitar la imagen del servicio?"
        description="El servicio se mostrará con el ícono de su categoría."
        confirmLabel="Quitar imagen"
        destructive
        onConfirm={async () => {
          onChanged(await (await getStoreServicesService()).removeImage(storeId, service.id))
          invalidateStore()
          toast.success('Quitamos la imagen del servicio.')
        }}
      />
    </div>
  )
}

interface ServiceFormProps {
  service: StoreServiceItem | undefined
  branches: Branch[]
  branchesKnown: boolean
  onDone: () => void
  onCancel: () => void
  onDirtyChange: (dirty: boolean) => void
}

function ServiceForm({ service: initialService, branches, branchesKnown, onDone, onCancel, onDirtyChange }: ServiceFormProps) {
  const { storeId, invalidateStore } = usePortal()  // La imagen cambia el servicio (y su versión) sin pasar por el formulario: se conserva la última vista.
  const [service, setService] = useState(initialService)
  const draft = useDraftForm<Values>({ initial: valuesOf(initialService), validate })
  const { values } = draft
  const submit = useSubmitOnce(async (input: StoreServiceInput) => {
    const api = await getStoreServicesService()
    if (service) await api.update(storeId, service.id, input)
    else await api.create(storeId, input)
  })
  const pending = submit.pending
  const blocker = useUnsavedChanges(draft.isDirty && !pending)
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    onDirtyChange(draft.isDirty && !pending)
    return () => onDirtyChange(false)
  }, [draft.isDirty, pending, onDirtyChange])

  const bind = (field: (typeof FIELDS)[number]) => ({ id: fieldId(field), error: draft.errorFor(field), onBlur: () => draft.touch(field) })
  const text = (field: 'name' | 'description' | 'priceFrom' | 'durationMinutes') => ({
    ...bind(field),
    value: values[field],
    onChange: (event: { target: { value: string } }) => draft.set(field, event.target.value),
  })

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(undefined)
    const check = draft.validateAll()
    if (!check.valid) {
      focusById(check.firstInvalid ? fieldId(check.firstInvalid) : undefined)
      return
    }
    const result = await submit.run(buildInput(values, service))
    if (result.status === 'ok') {
      draft.markSaved()
      onDirtyChange(false)
      invalidateStore()
      toast.success(service ? 'Servicio actualizado.' : 'Servicio creado. Ya puedes añadirle una imagen desde «Editar».')
      onDone()
    } else if (result.status === 'error') {
      const error = result.error
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        draft.setServerErrors(error.fieldErrors)
        const first = FIELDS.find((field) => error.fieldErrors[field])
        if (first) requestAnimationFrame(() => focusById(fieldId(first)))
      }
      if (error instanceof ApiError && error.code === 'SERVICE_VERSION_CONFLICT') {
        invalidateStore()
        setFormError('Otra persona modificó este servicio mientras lo editabas. Cierra el panel, revisa los datos actualizados y vuelve a aplicar tus cambios.')
      } else setFormError(actionErrorMessage(error, 'No pudimos guardar el servicio. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  const unknownBranchIds = values.branchIds.filter((id) => !branches.some((branch) => branch.id === id))
  function toggleBranch(id: string, checked: boolean) {
    draft.set('branchIds', checked ? [...values.branchIds, id] : values.branchIds.filter((current) => current !== id))
  }
  const branchError = draft.errorFor('branchIds')
  const switchId = fieldId('bookingEnabled')

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex min-h-0 flex-1 flex-col" aria-busy={pending || undefined}>
      <div className="flex flex-1 flex-col gap-4 overflow-y-auto px-4 pb-4">
        {formError ? (
          <FormAlert ref={alertRef} title="No se guardó el servicio">
            {formError}
          </FormAlert>
        ) : null}
        <SelectField
          {...bind('category')}
          label="Categoría"
          value={values.category}
          onChange={(event) => draft.set('category', event.target.value as ServiceCategory)}
          options={SERVICE_CATEGORY_CODES.map((code) => ({ value: code, label: serviceCategoryLabel(code) }))}
        />
        <TextField {...text('name')} label="Nombre" maxLength={120} autoComplete="off" required hint="Ej.: Alineación y balanceo." />
        <TextAreaField {...text('description')} label="Descripción" optional maxLength={1100} hint={`${values.description.length} de 1000 caracteres. Qué incluye y qué debe traer el cliente.`} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField {...text('priceFrom')} label="Precio desde (COP)" optional inputMode="decimal" autoComplete="off" hint="Vacío = «a cotizar». Es un precio de referencia." />
          <TextField {...text('durationMinutes')} label="Duración (minutos)" optional inputMode="numeric" autoComplete="off" hint="Lo que suele tardar el servicio." />
        </div>

        <fieldset className="flex flex-col gap-2" aria-describedby={branchError ? `${fieldId('branchIds')}-error` : undefined}>
          <legend className="text-sm font-semibold text-ap-ink">Sucursales que lo prestan</legend>
          {!branchesKnown ? (
            <p className="text-sm text-ap-muted">No pudimos cargar tus sucursales: tu rol no puede verlas o hubo un error. Se conservan las que ya estaban elegidas.</p>
          ) : branches.length === 0 ? (
            <p className="text-sm text-ap-muted">Aún no tienes sucursales activas. Crea una en «Sucursales» para que los compradores puedan agendar.</p>
          ) : null}
          {branches.map((branch, index) => (
            <label key={branch.id} className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border border-ap-line px-3 text-sm">
              <Checkbox id={index === 0 ? fieldId('branchIds') : undefined} checked={values.branchIds.includes(branch.id)} onCheckedChange={(checked) => toggleBranch(branch.id, checked === true)} />
              <span>{branch.name}</span>
            </label>
          ))}
          {unknownBranchIds.map((id) => (
            <label key={id} className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border border-ap-line px-3 text-sm text-ap-muted">
              <Checkbox checked onCheckedChange={(checked) => toggleBranch(id, checked === true)} />
              <span>Sucursal inactiva o fuera de tu vista</span>
            </label>
          ))}
          {branchError ? (
            <p id={`${fieldId('branchIds')}-error`} role="alert" className="text-sm font-medium text-ap-danger">
              {branchError}
            </p>
          ) : null}
        </fieldset>

        <div className="flex flex-col gap-3 rounded-md border border-ap-line bg-ap-paper p-3">
          <div className="flex items-start gap-3">
            <Switch id={switchId} checked={values.bookingEnabled} onCheckedChange={(checked) => draft.set('bookingEnabled', checked)} aria-labelledby={`${switchId}-label`} aria-describedby={`${switchId}-hint`} className="mt-0.5" />
            <div className="text-sm">
              <p id={`${switchId}-label`} className="font-semibold">
                Agenda en línea
              </p>
              <p id={`${switchId}-hint`} className="text-ap-muted">
                Los compradores pueden pedir cita desde la página del servicio. Tú la confirmas, propones otra hora o la rechazas.
              </p>
            </div>
          </div>
          {values.bookingEnabled
            ? branches
                .filter((branch) => values.branchIds.includes(branch.id) && !hasWeeklyHours(branch.openingHours))
                .map((branch) => (
                  <p key={branch.id} role="status" className="flex items-start gap-2 rounded-md border border-ap-warning/40 bg-ap-warning-soft p-3 text-sm text-ap-ink">
                    <TriangleAlertIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
                    <span>
                      Para recibir citas, configura el horario de {branch.name} en{' '}
                      <Link to={`/portal/${encodeURIComponent(storeId)}/sucursales`} className="font-semibold underline underline-offset-4">
                        Sucursales
                      </Link>
                      .
                    </span>
                  </p>
                ))
            : null}
          {values.bookingEnabled ? (
            <SelectField
              {...bind('slotMinutes')}
              label="Duración de cada franja"
              value={values.slotMinutes}
              onChange={(event) => draft.set('slotMinutes', event.target.value)}
              options={SLOT_MINUTES_OPTIONS.map((minutes) => ({ value: String(minutes), label: `${minutes} minutos` }))}
              hint="Cada hora que se ofrece al comprador dura esto."
            />
          ) : null}
        </div>

        {service ? <ServiceImage service={service} onChanged={setService} /> : <p className="text-xs text-ap-muted">Podrás subir la imagen cuando termines de crear el servicio.</p>}
      </div>
      <div className="flex flex-col-reverse gap-2 border-t border-border p-4 sm:flex-row sm:justify-end">
        <Button type="button" variant="outline" className="h-11 px-5" onClick={onCancel} disabled={pending}>
          Cancelar
        </Button>
        <Button type="submit" className="h-11 px-5" loading={pending} loadingText="Guardando…">
          {service ? 'Guardar cambios' : 'Crear servicio'}
        </Button>
      </div>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}

interface ServiceSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  service: StoreServiceItem | undefined
  branches: Branch[]
  branchesKnown: boolean
}

/** Alta y edición de un servicio en un panel lateral (`Sheet`). */
export function ServiceSheet({ open, onOpenChange, service, branches, branchesKnown }: ServiceSheetProps) {
  const guard = useCloseGuard(() => onOpenChange(false))
  return (
    <Sheet open={open} onOpenChange={(next) => (next ? onOpenChange(true) : guard.requestClose())}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-lg">
        <SheetHeader className="p-4 pr-16">
          <SheetTitle className="text-block-title">{service ? 'Editar servicio' : 'Nuevo servicio'}</SheetTitle>
          <SheetDescription>{service ? 'Cambia los datos, la agenda o las sucursales.' : 'Describe el servicio y dónde lo prestas.'}</SheetDescription>
        </SheetHeader>
        <ServiceForm
          key={service?.id ?? 'nuevo'}
          service={service}
          branches={branches}
          branchesKnown={branchesKnown}
          onDone={() => onOpenChange(false)}
          onCancel={guard.requestClose}
          onDirtyChange={guard.setDirty}
        />
        <DiscardChangesDialog guard={guard} />
      </SheetContent>
    </Sheet>
  )
}
