import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ArrowLeftIcon, ArrowRightIcon, EyeIcon, EyeOffIcon, PackageSearchIcon, SaveIcon, Trash2Icon } from 'lucide-react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { FormSection } from '@/components/common/form-section'
import { SmartImage } from '@/components/common/smart-image'
import { EmptyState } from '@/components/common/states'
import { Stepper, type StepStatus } from '@/components/common/stepper'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { ApiError } from '@/lib/api'
import { formatCOP, formatNumber } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { useDraftForm, type FieldErrors } from '@/lib/use-draft-form'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import { getBranchService } from '../api/branch-service'
import { getProductService } from '../api/product-service'
import type { ProductCreateInput, ProductOptions, ProductRow, ProductUpdateInput, PublishBlocker, StoreManufacturer, VehicleRule } from '../api/types'
import { ConfirmDialog } from '../components/confirm-dialog'
import { CopField } from '../components/cop-field'
import { SelectField, TextAreaField } from '../components/form-controls'
import { ListSkeleton } from '../components/list-skeleton'
import { ManufacturerCombobox, type ManufacturerChoice } from '../components/manufacturer-combobox'
import { PageHeader } from '../components/page-header'
import { ProductGalleryEditor } from '../components/product-gallery-editor'
import { ChecklistDetail, PublishChecklist } from '../components/publish-checklist'
import { StatusBadge, StockBadge } from '../components/status-badge'
import { VehicleRulesPicker } from '../components/vehicle-rules-picker'
import { QueryError } from '../components/query-boundary'
import { usePortal } from '../context'
import { focusMain, usePublishProduct, useStoreQuery } from '../hooks'
import { actionErrorMessage, publishBlockers } from '../lib/errors'
import { focusById } from '../lib/forms'
import { humanizeCode } from '../lib/labels'
import { createImagePart, editImagePart, galleryChanged, galleryFrom, mainKeyFrom, releasePreviews, resolveMainKey, thumbOf, type GalleryItem } from '../lib/product-gallery'
import { buildChecklist, type StepId } from '../lib/publish-checklist'
import { ruleLabel, toRuleInputs } from '../lib/vehicle-rules'

interface Values {
  name: string
  sku: string
  partNumber: string
  description: string
  categoryId: string
  branchId: string
  /** Solo dígitos: el valor numérico sale de `Number(price)`. */
  price: string
  quantity: string
  manufacturerId: string
  newManufacturerName: string
  confirmNewManufacturer: boolean
  /** Producto que no depende del vehículo. Las reglas escritas se conservan en memoria pero no se envían. */
  universal: boolean
  vehicleRules: VehicleRule[]
  gallery: GalleryItem[]
  mainKey: string
}
type Field = keyof Values

interface StepDef {
  id: StepId
  label: string
  fields: Field[]
}
const STEPS: StepDef[] = [
  { id: 'producto', label: 'Producto', fields: ['name', 'manufacturerId', 'categoryId', 'description', 'sku', 'partNumber'] },
  { id: 'vehiculos', label: 'Vehículos', fields: ['vehicleRules'] },
  { id: 'precio', label: 'Precio y stock', fields: ['branchId', 'price', 'quantity'] },
  { id: 'imagenes', label: 'Imágenes', fields: ['gallery'] },
  { id: 'revision', label: 'Revisión', fields: [] },
]
const stepIndex = (id: StepId) => STEPS.findIndex((step) => step.id === id)
const stepOfField = (field: string): StepId => STEPS.find((step) => (step.fields as string[]).includes(field))?.id ?? 'producto'

const fieldId = (field: string) => `producto-${field}`
/** El control que recibe el foco cuando un campo tiene error. */
const focusIdOf = (field: string) => (field === 'gallery' ? `${fieldId('gallery')}-agregar` : field === 'vehicleRules' ? `${fieldId('vehicleRules')}-marca` : fieldId(field))

function valuesFrom(row: ProductRow | undefined, branchId: string): Values {
  return {
    name: row?.name ?? '',
    sku: row?.sku ?? '',
    partNumber: row?.partNumber ?? '',
    description: row?.description ?? '',
    categoryId: row?.categoryId ?? '',
    branchId: row?.branchId ?? branchId,
    price: row ? String(Math.round(row.price)) : '',
    quantity: row ? String(row.quantity) : '',
    manufacturerId: row?.manufacturerId ?? '',
    newManufacturerName: '',
    confirmNewManufacturer: false,
    universal: row?.universal ?? false,
    vehicleRules: row?.vehicleRules ?? [],
    gallery: row ? galleryFrom(row.images) : [],
    mainKey: row ? mainKeyFrom(row.images) : '',
  }
}

/** Mismas reglas que `ProductCreateRequest` para guardar un borrador; el servidor las vuelve a comprobar. */
function validate(values: Values): FieldErrors {
  const errors: FieldErrors = {}
  if (!values.name.trim()) errors.name = 'Escribe el nombre del producto.'
  else if (values.name.trim().length > 200) errors.name = 'El nombre no puede superar 200 caracteres.'
  if (values.sku.trim().length > 100) errors.sku = 'El SKU no puede superar 100 caracteres.'
  if (values.partNumber.trim().length > 50) errors.partNumber = 'La referencia no puede superar 50 caracteres.'
  if (!values.description.trim()) errors.description = 'Describe el producto para que los compradores lo entiendan.'
  const newName = values.newManufacturerName.trim()
  if (!values.manufacturerId && !newName) errors.manufacturerId = 'Elige el fabricante o agrega uno nuevo.'
  else if (!values.manufacturerId && (newName.length < 2 || newName.length > 100)) errors.manufacturerId = 'El nombre del fabricante debe tener entre 2 y 100 caracteres.'
  if (!values.categoryId) errors.categoryId = 'Elige la categoría.'
  if (!values.branchId) errors.branchId = 'Elige la sucursal donde está el producto.'
  if (!values.price) errors.price = 'Escribe el precio en pesos.'
  else if (Number(values.price) < 1) errors.price = 'El precio debe ser mayor que 0.'
  if (!values.quantity) errors.quantity = 'Escribe cuántas unidades tienes (0 si no hay).'
  return errors
}

/** Campo del formulario al que apunta una clave de error del servidor (`details` por campo). */
function fieldForServerKey(key: string): Field | undefined {
  if (key.startsWith('vehicleRules')) return 'vehicleRules'
  if (key === 'newManufacturerName' || key === 'manufacturerId') return 'manufacturerId'
  if (/^(images|newImages|mainImage|mainImageIndex|removeImageIds|imageOrder)/.test(key)) return 'gallery'
  return STEPS.some((step) => (step.fields as string[]).includes(key)) ? (key as Field) : undefined
}

function readSuggestions(details: unknown): { id: string; name: string }[] {
  if (!Array.isArray(details)) return []
  return details.filter((item): item is { id: string; name: string } => !!item && typeof item === 'object' && typeof item.id === 'string' && typeof item.name === 'string')
}

function readRuleErrors(details: unknown): Record<number, string> {
  if (!Array.isArray(details)) return {}
  const result: Record<number, string> = {}
  for (const item of details) {
    if (item && typeof item === 'object' && typeof item.index === 'number') result[item.index] = typeof item.message === 'string' ? item.message : 'Esta regla no es válida.'
  }
  return result
}

type Intent = 'draft' | 'publish' | 'unpublish' | 'save'

type Job =
  | { mode: 'create'; input: ProductCreateInput; files: File[] }
  | { mode: 'edit'; branchId: string; productId: string; input: ProductUpdateInput; files: File[] }

function SummaryRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:gap-4">
      <dt className="shrink-0 text-sm text-muted-foreground sm:w-36">{label}</dt>
      <dd className="min-w-0 text-sm font-medium">{children}</dd>
    </div>
  )
}

function ProductForm({
  mode,
  rows,
  options,
  manufacturers: initialManufacturers,
  branches,
}: {
  mode: 'create' | 'edit'
  rows: ProductRow[]
  options: ProductOptions
  manufacturers: StoreManufacturer[]
  branches: { value: string; label: string }[]
}) {
  const { storeId, can, promptUpgrade, invalidateStore } = usePortal()
  const navigate = useNavigate()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const first = rows[0]

  const [initial] = useState(() => valuesFrom(first, branches[0]?.value ?? ''))
  const draft = useDraftForm<Values>({ initial, validate })
  const { values } = draft

  const [step, setStep] = useState<StepId>('producto')
  const [left, setLeft] = useState<StepId[]>(() => (mode === 'edit' ? STEPS.filter((item) => item.id !== 'revision').map((item) => item.id) : []))
  const [focusReq, setFocusReq] = useState<{ step: StepId; field?: string; n: number }>()
  const [blockers, setBlockers] = useState<PublishBlocker[]>([])
  const [ruleErrors, setRuleErrors] = useState<Record<number, string>>({})
  const [similarServer, setSimilarServer] = useState<{ name: string; suggestions: { id: string; name: string }[] }>()
  const [formError, setFormError] = useState<string>()
  const [alertNonce, setAlertNonce] = useState(0)
  const [pendingIntent, setPendingIntent] = useState<Intent>()
  const alertRef = useRef<HTMLDivElement>(null)
  const lastIntent = useRef<Intent>('draft')
  const focusSeq = useRef(0)

  const manufacturers = useMemo(() => {
    if (first && !initialManufacturers.some((item) => item.id === first.manufacturerId)) {
      return [...initialManufacturers, { id: first.manufacturerId, name: first.manufacturerName, approved: first.manufacturerApproved }]
    }
    return initialManufacturers
  }, [initialManufacturers, first])

  const submitOnce = useSubmitOnce(async (job: Job) => {
    const service = await getProductService()
    return job.mode === 'create'
      ? service.create(storeId, job.input, job.files)
      : service.update(storeId, job.branchId, job.productId, job.input, job.files)
  })
  const pending = submitOnce.pending
  const savedRef = useRef(false)
  const blocker = useUnsavedChanges(() => draft.isDirty && !savedRef.current)

  // Libera las URL de previsualización al salir del formulario (los pasos se montan y desmontan).
  const galleryRef = useRef(values.gallery)
  useEffect(() => {
    galleryRef.current = values.gallery
  })
  useEffect(() => () => releasePreviews(galleryRef.current), [])

  // Foco al cambiar de paso: primer error o el encabezado del paso.
  useEffect(() => {
    if (!focusReq) return
    if (focusReq.field) focusById(focusIdOf(focusReq.field))
    else document.getElementById(`paso-${focusReq.step}-titulo`)?.focus()
  }, [focusReq])

  useEffect(() => {
    if (alertNonce > 0) alertRef.current?.focus()
  }, [alertNonce])

  function goTo(target: StepId, field?: string) {
    setStep(target)
    setFocusReq({ step: target, field, n: (focusSeq.current += 1) })
  }

  function markLeft(ids: StepId[]) {
    setLeft((current) => [...new Set([...current, ...ids])])
  }

  /** Muestra los errores del paso y devuelve su primer campo inválido. */
  function checkStep(def: StepDef, errors: FieldErrors): Field | undefined {
    for (const field of def.fields) draft.touch(field)
    return def.fields.find((field) => errors[field])
  }

  function requestStep(target: StepId) {
    const from = stepIndex(step)
    const to = stepIndex(target)
    if (to === from) return
    if (mode === 'create' && to > from) {
      const errors = validate(values)
      for (const def of STEPS.slice(from, to)) {
        const bad = checkStep(def, errors)
        markLeft([def.id])
        if (bad) {
          goTo(def.id, bad)
          return
        }
      }
    } else if (to < from) {
      markLeft([step])
    }
    goTo(target)
  }

  const goNext = () => {
    const next = STEPS[stepIndex(step) + 1]
    if (next) requestStep(next.id)
  }
  const goPrevious = () => {
    const previous = STEPS[stepIndex(step) - 1]
    if (previous) requestStep(previous.id)
  }

  function onFormSubmit(event: FormEvent<HTMLFormElement>) {
    // Enter dentro de un campo avanza de paso; nunca envía el formulario (eso es solo de los botones finales).
    event.preventDefault()
    if (step !== 'revision') goNext()
  }

  function stepStatus(def: StepDef): StepStatus {
    if (def.fields.some((field) => draft.errorFor(field))) return 'error'
    if (!left.includes(def.id)) return 'idle'
    if (def.fields.some((field) => draft.errors[field])) return 'error'
    if (def.id === 'vehiculos' && !values.universal && values.vehicleRules.length === 0) return 'idle'
    if (def.id === 'imagenes' && values.gallery.length === 0) return 'idle'
    return 'complete'
  }

  function clearServerFeedback() {
    setBlockers([])
    setRuleErrors({})
    setSimilarServer(undefined)
    setFormError(undefined)
  }

  function handleError(error: unknown, submitted: Values) {
    if (promptUpgrade(error)) return
    const notPublishable = publishBlockers(error)
    if (notPublishable) {
      setBlockers(notPublishable)
      goTo('revision')
      setAlertNonce((n) => n + 1)
      return
    }
    if (error instanceof ApiError) {
      if (error.code === 'MANUFACTURER_SIMILAR') {
        setSimilarServer({ name: submitted.newManufacturerName, suggestions: readSuggestions(error.details) })
        goTo('producto', 'manufacturerId')
        return
      }
      if (error.code === 'VEHICLE_RULE_INVALID') {
        setRuleErrors(readRuleErrors(error.details))
        setFormError('Alguna regla de vehículo no es válida. Revisa las marcadas y corrígelas.')
        goTo('vehiculos')
        setAlertNonce((n) => n + 1)
        return
      }
      const fieldCodes: Record<string, Field> = {
        MANUFACTURER_PENDING_LIMIT: 'manufacturerId',
        MANUFACTURER_NAME_UNAVAILABLE: 'manufacturerId',
        PRODUCT_IMAGE_LIMIT_REACHED: 'gallery',
      }
      const byCode = error.code ? fieldCodes[error.code] : undefined
      if (byCode) {
        draft.setServerErrors({ [byCode]: error.message })
        goTo(stepOfField(byCode), byCode)
        return
      }
      const mapped: FieldErrors = {}
      for (const [key, message] of Object.entries(error.fieldErrors)) {
        const field = fieldForServerKey(key)
        if (field && !mapped[field]) mapped[field] = message
      }
      const firstField = STEPS.flatMap((def) => def.fields).find((field) => mapped[field])
      if (firstField) {
        draft.setServerErrors(mapped)
        goTo(stepOfField(firstField), firstField)
        setFormError('Hay datos por corregir. Los marcamos en el paso correspondiente.')
        return
      }
    }
    setFormError(actionErrorMessage(error, 'No pudimos guardar el producto. Inténtalo de nuevo.'))
    setAlertNonce((n) => n + 1)
  }

  async function submit(intent: Intent, overrides?: Partial<Values>) {
    clearServerFeedback()
    lastIntent.current = intent
    const current = { ...values, ...overrides }
    const check = draft.validateAll()
    if (!check.valid) {
      markLeft(STEPS.map((def) => def.id))
      const badStep = STEPS.find((def) => def.fields.some((field) => check.errors[field]))
      const badField = badStep?.fields.find((field) => check.errors[field])
      if (badStep) goTo(badStep.id, badField)
      setFormError(`Falta completar datos del paso “${badStep?.label ?? 'Producto'}”. No se guardó nada.`)
      return
    }
    const price = Number(current.price)
    const quantity = Number(current.quantity)
    const newName = current.newManufacturerName.trim()
    let job: Job
    if (mode === 'create') {
      const part = createImagePart(current.gallery, current.mainKey)
      const input: ProductCreateInput = {
        branchId: current.branchId,
        name: current.name.trim(),
        sku: current.sku.trim() || undefined,
        partNumber: current.partNumber.trim() || undefined,
        description: current.description.trim(),
        price,
        quantity,
        categoryId: current.categoryId,
        ...(current.manufacturerId ? { manufacturerId: current.manufacturerId } : { newManufacturerName: newName, ...(current.confirmNewManufacturer ? { confirmNewManufacturer: true } : {}) }),
        universal: current.universal,
        vehicleRules: current.universal ? [] : toRuleInputs(current.vehicleRules),
        ...(part.mainImageIndex !== undefined ? { mainImageIndex: part.mainImageIndex } : {}),
        publish: intent === 'publish',
      }
      job = { mode: 'create', input, files: part.files }
    } else {
      const row = rows.find((candidate) => candidate.branchId === current.branchId) ?? first
      if (!row) return
      const input: ProductUpdateInput = {}
      if (current.name.trim() !== row.name) input.name = current.name.trim()
      if (current.sku.trim() !== (row.sku ?? '')) input.sku = current.sku.trim()
      if (current.partNumber.trim() !== (row.partNumber ?? '')) input.partNumber = current.partNumber.trim()
      if (current.description.trim() !== row.description) input.description = current.description.trim()
      if (newName) {
        input.newManufacturerName = newName
        if (current.confirmNewManufacturer) input.confirmNewManufacturer = true
      } else if (current.manufacturerId !== row.manufacturerId) input.manufacturerId = current.manufacturerId
      if (current.categoryId !== row.categoryId) input.categoryId = current.categoryId
      if (price !== row.price) input.price = price
      if (quantity !== row.quantity) input.quantity = quantity
      const currentRules = toRuleInputs(row.vehicleRules)
      const nextRules = toRuleInputs(current.vehicleRules)
      const key = (rule: { makeId: string; modelId?: string; versionId?: string }) => `${rule.makeId}:${rule.modelId ?? ''}:${rule.versionId ?? ''}`
      const rulesChanged = nextRules.length !== currentRules.length || !nextRules.every((rule) => currentRules.some((other) => key(other) === key(rule)))
      if (current.universal) {
        // Universal: nunca se envían reglas; pasar a universal las borra en el servidor.
        if (!row.universal) input.universal = true
      } else if (row.universal) {
        input.universal = false
        input.vehicleRules = nextRules
      } else if (rulesChanged) input.vehicleRules = nextRules
      const part = editImagePart(row.images, mainKeyFrom(row.images), current.gallery, current.mainKey)
      if (part.removeImageIds) input.removeImageIds = part.removeImageIds
      if (part.imageOrder) input.imageOrder = part.imageOrder
      if (part.mainImage) input.mainImage = part.mainImage
      if (intent === 'publish') input.publish = true
      if (intent === 'unpublish') input.publish = false
      if (Object.keys(input).length === 0 && !galleryChanged(part)) {
        toast.info('No hay cambios para guardar.')
        return
      }
      job = { mode: 'edit', branchId: row.branchId, productId: row.id, input, files: part.files }
    }
    setPendingIntent(intent)
    const result = await submitOnce.run(job)
    setPendingIntent(undefined)
    if (result.status === 'busy') return
    if (result.status === 'error') {
      handleError(result.error, current)
      return
    }
    // Se marca como guardado con una referencia: el guardia se evalúa al navegar, antes del siguiente render.
    savedRef.current = true
    draft.markSaved()
    if (mode === 'create') {
      toast.success(intent === 'publish' ? `“${result.value.name}” se creó y ya está publicado.` : 'Borrador guardado. Completa lo que falte y publícalo cuando esté listo.')
      navigate(intent === 'publish' ? `${base}/productos` : `${base}/productos/${encodeURIComponent(result.value.id)}`, { replace: true })
    } else {
      toast.success(intent === 'publish' ? 'Cambios guardados y producto publicado.' : intent === 'unpublish' ? 'Cambios guardados y producto retirado del marketplace.' : 'Cambios guardados.')
      navigate(`${base}/productos`, { replace: true })
    }
    invalidateStore()
  }

  function changeBranch(branchId: string) {
    const row = rows.find((candidate) => candidate.branchId === branchId)
    draft.patch({ branchId, ...(row ? { price: String(Math.round(row.price)), quantity: String(row.quantity) } : {}) })
  }

  function setManufacturer(choice: ManufacturerChoice) {
    setSimilarServer(undefined)
    draft.patch({ manufacturerId: choice.id, newManufacturerName: choice.newName, confirmNewManufacturer: choice.confirmed })
  }

  const checklist = buildChecklist({
    images: values.gallery.length,
    rules: values.vehicleRules.length,
    universal: values.universal,
    hasManufacturer: Boolean(values.manufacturerId || values.newManufacturerName.trim()),
    price: values.price ? Number(values.price) : undefined,
    description: values.description,
    quantity: values.quantity ? Number(values.quantity) : undefined,
    subscriptionInactive: blockers.some((item) => item.code === 'SUBSCRIPTION_INACTIVE'),
  })

  const categoryOptions = options.categories.map((item) => ({ value: item.id, label: humanizeCode(item.code) }))
  const manufacturerName = values.manufacturerId ? (manufacturers.find((item) => item.id === values.manufacturerId)?.name ?? '') : values.newManufacturerName.trim()
  const published = first?.state === 'ACTIVE'
  const canPublish = can('PRODUCT_PUBLISH')
  const stepDef = STEPS[stepIndex(step)] as StepDef
  const mainItem = values.gallery.find((item) => item.key === resolveMainKey(values.gallery, values.mainKey))
  const busy = pending
  const label = (intent: Intent, idle: string) => (pendingIntent === intent ? 'Guardando…' : idle)

  const blockerStep: Record<PublishBlocker['code'], StepId> = { NO_IMAGES: 'imagenes', NO_VEHICLES: 'vehiculos', MANUFACTURER_INVALID: 'producto', SUBSCRIPTION_INACTIVE: 'revision' }

  function renderStep() {
    switch (step) {
      case 'producto':
        return (
          <FormSection id="paso-producto" title="Producto" description="Lo básico para que los compradores lo encuentren y lo entiendan." disabled={busy}>
            <TextField
              id={fieldId('name')}
              label="Nombre"
              value={values.name}
              onChange={(event) => draft.set('name', event.target.value)}
              onBlur={() => draft.touch('name')}
              error={draft.errorFor('name')}
              maxLength={200}
              autoComplete="off"
              required
              hint="Como lo buscaría un comprador. Ej.: Pastillas de freno delanteras."
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <ManufacturerCombobox
                id={fieldId('manufacturerId')}
                storeId={storeId}
                options={manufacturers}
                value={{ id: values.manufacturerId, newName: values.newManufacturerName, confirmed: values.confirmNewManufacturer }}
                onChange={setManufacturer}
                onBlur={() => draft.touch('manufacturerId')}
                error={draft.errorFor('manufacturerId')}
                hint="Escribe para buscar. Si no está, agrégalo: solo tu almacén lo ve como opción hasta que un administrador lo apruebe; puedes publicar con él."
                required
                disabled={busy}
              />
              <SelectField
                id={fieldId('categoryId')}
                label="Categoría"
                placeholder="Elige una categoría"
                options={categoryOptions}
                value={values.categoryId}
                onChange={(event) => draft.set('categoryId', event.target.value)}
                onBlur={() => draft.touch('categoryId')}
                error={draft.errorFor('categoryId')}
                required
              />
            </div>
            {similarServer ? (
              <div role="alert" className="flex flex-col gap-2 rounded-lg border border-stock-low/40 bg-stock-low/10 p-3">
                <p className="text-sm font-semibold">¿Quisiste decir…?</p>
                <p className="text-sm text-muted-foreground">
                  El servidor encontró fabricantes con un nombre parecido a “{similarServer.name}”. Elige uno para no duplicarlo, o confirma que es distinto.
                </p>
                <div className="flex flex-wrap gap-2">
                  {similarServer.suggestions.map((suggestion) => (
                    <Button
                      key={suggestion.id}
                      type="button"
                      variant="outline"
                      className="h-11 px-4"
                      onClick={() => {
                        setSimilarServer(undefined)
                        draft.patch({ manufacturerId: suggestion.id, newManufacturerName: '', confirmNewManufacturer: false })
                      }}
                    >
                      {suggestion.name}
                    </Button>
                  ))}
                  <Button
                    type="button"
                    className="h-11 px-4"
                    disabled={busy}
                    onClick={() => {
                      setSimilarServer(undefined)
                      draft.patch({ confirmNewManufacturer: true })
                      void submit(lastIntent.current, { confirmNewManufacturer: true })
                    }}
                  >
                    No, crear “{similarServer.name}” y guardar
                  </Button>
                </div>
              </div>
            ) : null}
            <TextAreaField
              id={fieldId('description')}
              label="Descripción"
              value={values.description}
              onChange={(event) => draft.set('description', event.target.value)}
              onBlur={() => draft.touch('description')}
              error={draft.errorFor('description')}
              required
              hint="Explícalo como en el mostrador: qué es, material, medidas, posición (delantero/trasero, izquierdo/derecho), garantía y detalles de compatibilidad."
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                id={fieldId('sku')}
                label="SKU"
                optional
                value={values.sku}
                onChange={(event) => draft.set('sku', event.target.value)}
                onBlur={() => draft.touch('sku')}
                error={draft.errorFor('sku')}
                maxLength={100}
                autoComplete="off"
                hint="Tu código interno para ubicar el producto en tu inventario."
              />
              <TextField
                id={fieldId('partNumber')}
                label="Referencia del fabricante"
                optional
                value={values.partNumber}
                onChange={(event) => draft.set('partNumber', event.target.value)}
                onBlur={() => draft.touch('partNumber')}
                error={draft.errorFor('partNumber')}
                maxLength={50}
                autoComplete="off"
                hint="El número de parte que pone el fabricante (OEM o de catálogo); ayuda a encontrar la pieza exacta."
              />
            </div>
          </FormSection>
        )
      case 'vehiculos':
        return (
          <FormSection
            id="paso-vehiculos"
            title="Compatibilidad"
            description="Marca → modelo → versión. «Todos los modelos» (o «todas las versiones») significa que sirve para cualquiera de esa marca, incluidos los que se agreguen después."
            disabled={busy}
          >
            <div className="flex flex-col gap-1 rounded-lg bg-card p-3 ring-1 ring-foreground/10">
              <label htmlFor={fieldId('universal')} className="flex min-h-9 cursor-pointer items-center gap-3 text-sm font-medium pointer-coarse:min-h-11">
                <Checkbox
                  id={fieldId('universal')}
                  checked={values.universal}
                  onCheckedChange={(checked) => {
                    setRuleErrors({})
                    draft.set('universal', checked === true)
                  }}
                  disabled={busy}
                  aria-describedby={`${fieldId('universal')}-ayuda`}
                />
                Este producto no depende del vehículo (universal)
              </label>
              <p id={`${fieldId('universal')}-ayuda`} className="pl-7 text-xs text-muted-foreground">
                Aceites, herramientas, accesorios… se muestra en cualquier búsqueda por vehículo.
              </p>
            </div>
            {values.universal ? (
              <p role="status" className="text-sm text-muted-foreground">
                Se publicará como universal y no se enviarán reglas de vehículo.
                {values.vehicleRules.length > 0 ? ` Conservamos las ${values.vehicleRules.length} que escribiste por si desactivas la opción.` : ''}
              </p>
            ) : (
              <>
                <VehicleRulesPicker
                  id={fieldId('vehicleRules')}
                  value={values.vehicleRules}
                  onChange={(rules) => {
                    setRuleErrors({})
                    draft.set('vehicleRules', rules)
                  }}
                  onBlur={() => draft.touch('vehicleRules')}
                  error={draft.errorFor('vehicleRules')}
                  ruleErrors={ruleErrors}
                  disabled={busy}
                />
                <p className="text-sm text-muted-foreground">Puedes guardar un borrador sin vehículos, pero para publicar necesitas al menos uno o marcar el producto como universal.</p>
              </>
            )}
          </FormSection>
        )
      case 'precio':
        return (
          <FormSection id="paso-precio" title="Precio y existencias" description="Son propios de cada sucursal." disabled={busy}>
            {branches.length > 1 ? (
              <SelectField
                id={fieldId('branchId')}
                label="Sucursal"
                placeholder={mode === 'create' ? 'Elige una sucursal' : undefined}
                options={branches}
                value={values.branchId}
                onChange={(event) => (mode === 'edit' ? changeBranch(event.target.value) : draft.set('branchId', event.target.value))}
                onBlur={() => draft.touch('branchId')}
                error={draft.errorFor('branchId')}
                hint={mode === 'edit' ? 'Al cambiar de sucursal verás su precio y sus existencias.' : 'Dónde está físicamente el producto.'}
                required
              />
            ) : (
              <p className="text-sm text-muted-foreground">
                Sucursal: <strong className="font-semibold text-foreground">{branches[0]?.label ?? 'sin sucursal activa'}</strong>
              </p>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <CopField
                id={fieldId('price')}
                label="Precio"
                value={values.price}
                onChange={(digits) => draft.set('price', digits)}
                onBlur={() => draft.touch('price')}
                error={draft.errorFor('price')}
                required
                hint="Precio de venta en pesos colombianos, sin decimales. Ej.: 85.000."
              />
              <TextField
                id={fieldId('quantity')}
                label="Existencias"
                inputMode="numeric"
                value={values.quantity}
                onChange={(event) => draft.set('quantity', event.target.value.replace(/\D/g, '').slice(0, 9))}
                onBlur={() => draft.touch('quantity')}
                error={draft.errorFor('quantity')}
                autoComplete="off"
                required
                hint="Unidades disponibles hoy en esta sucursal. Con 0 puedes publicar, pero aparecerá como agotado."
              />
            </div>
          </FormSection>
        )
      case 'imagenes':
        return (
          <FormSection
            id="paso-imagenes"
            title="Imágenes"
            description="La principal es la que ven primero los compradores. Nada se sube hasta que guardes el producto."
            disabled={busy}
          >
            <ProductGalleryEditor
              id={fieldId('gallery')}
              items={values.gallery}
              mainKey={values.mainKey}
              onChange={(items, mainKey) => draft.patch({ gallery: items, mainKey })}
              productName={values.name}
              error={draft.errorFor('gallery')}
              disabled={busy}
            />
          </FormSection>
        )
      case 'revision':
        return (
          <FormSection id="paso-revision" title="Revisión" description="Comprueba el resumen. Al final eliges si lo guardas como borrador o lo publicas." disabled={busy}>
            <ChecklistDetail items={checklist} onGoTo={(target) => goTo(target)} />
            <div className="flex flex-col gap-4 rounded-xl bg-card p-4 ring-1 ring-foreground/10 sm:flex-row">
              {mainItem ? (
                <SmartImage
                  src={thumbOf(mainItem)}
                  alt={`Imagen principal de ${values.name || 'el producto'}`}
                  width={160}
                  height={160}
                  frameClassName="aspect-square w-full shrink-0 rounded-lg sm:w-40"
                />
              ) : (
                <div className="grid aspect-square w-full shrink-0 place-items-center rounded-lg bg-concrete p-3 text-center text-xs text-muted-foreground sm:w-40">Sin imagen principal</div>
              )}
              <dl className="flex min-w-0 flex-1 flex-col gap-2">
                <SummaryRow label="Producto">{values.name.trim() || <span className="text-destructive">Falta el nombre</span>}</SummaryRow>
                <SummaryRow label="Fabricante">
                  {manufacturerName ? (
                    <span className="flex flex-wrap items-center gap-2">
                      {manufacturerName}
                      {values.newManufacturerName ? <StatusBadge tone="info">Nuevo · pendiente de revisión</StatusBadge> : null}
                    </span>
                  ) : (
                    <span className="text-destructive">Falta el fabricante</span>
                  )}
                </SummaryRow>
                <SummaryRow label="Categoría">{categoryOptions.find((item) => item.value === values.categoryId)?.label ?? <span className="text-destructive">Falta la categoría</span>}</SummaryRow>
                <SummaryRow label="Precio">{values.price ? formatCOP(Number(values.price)) : <span className="text-destructive">Falta el precio</span>}</SummaryRow>
                <SummaryRow label="Existencias">
                  <span className="flex flex-wrap items-center gap-2">
                    {values.quantity ? `${formatNumber(Number(values.quantity))} u.` : '—'}
                    {values.quantity ? <StockBadge quantity={Number(values.quantity)} /> : null}
                  </span>
                </SummaryRow>
                <SummaryRow label="Sucursal">{branches.find((item) => item.value === values.branchId)?.label ?? '—'}</SummaryRow>
                <SummaryRow label="Vehículos">
                  {values.universal ? (
                    <StatusBadge tone="info">Universal</StatusBadge>
                  ) : values.vehicleRules.length === 0 ? (
                    'Ninguno todavía'
                  ) : (
                    <ul className="flex flex-col gap-0.5">
                      {values.vehicleRules.slice(0, 6).map((rule) => (
                        <li key={`${rule.makeId}:${rule.modelId}:${rule.versionId}`}>{ruleLabel(rule)}</li>
                      ))}
                      {values.vehicleRules.length > 6 ? <li className="text-muted-foreground">y {values.vehicleRules.length - 6} más</li> : null}
                    </ul>
                  )}
                </SummaryRow>
                <SummaryRow label="Imágenes">{values.gallery.length === 0 ? 'Ninguna todavía' : `${values.gallery.length} (${values.gallery.filter((item) => item.kind === 'new').length} nuevas)`}</SummaryRow>
              </dl>
            </div>
          </FormSection>
        )
    }
  }

  return (
    <form noValidate onSubmit={onFormSubmit} className="flex max-w-5xl flex-col gap-6" aria-busy={pending || undefined}>
      <Stepper
        steps={STEPS.map((def) => ({ id: def.id, label: def.label, status: stepStatus(def) }))}
        current={step}
        onSelect={(id) => requestStep(id as StepId)}
        label="Pasos para registrar el producto"
      />

      {blockers.length > 0 ? (
        <div ref={alertRef} tabIndex={-1} role="alert" className="flex flex-col gap-2 rounded-lg border border-destructive/25 bg-destructive/5 p-4 text-sm outline-none">
          <p className="font-semibold text-destructive">No se pudo publicar: no se guardó nada. Falta lo siguiente</p>
          <ul className="flex flex-col gap-1.5">
            {blockers.map((item) => (
              <li key={item.code} className="flex flex-wrap items-center gap-2">
                <span>{item.message}</span>
                {item.code === 'SUBSCRIPTION_INACTIVE' ? (
                  <Button type="button" variant="link" size="sm" className="h-8 px-1 pointer-coarse:h-11" nativeButton={false} render={<Link to={`${base}/plan`} />}>
                    Ver mi plan
                  </Button>
                ) : (
                  <Button type="button" variant="link" size="sm" className="h-8 px-1 pointer-coarse:h-11" onClick={() => goTo(blockerStep[item.code])}>
                    Ir al paso
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <div>
            <Button type="button" variant="outline" className="h-11 px-4" disabled={busy} onClick={() => void submit(mode === 'create' ? 'draft' : 'save')}>
              {mode === 'create' ? 'Guardar como borrador' : 'Guardar cambios sin publicar'}
            </Button>
          </div>
        </div>
      ) : formError ? (
        <FormAlert ref={alertRef} title="No se guardó el producto">
          {formError}
        </FormAlert>
      ) : null}

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        {step !== 'revision' ? <PublishChecklist items={checklist} onGoTo={(target) => goTo(target)} className="lg:sticky lg:top-4 lg:col-start-2 lg:row-start-1" /> : null}
        <div className="min-w-0 lg:col-start-1 lg:row-start-1">{renderStep()}</div>
      </div>

      <div className="sticky bottom-0 z-10 -mx-4 flex flex-wrap items-center gap-2 border-t border-border bg-background/95 px-4 py-3 backdrop-blur sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
        <Button type="button" variant="outline" className="h-11 px-4" onClick={goPrevious} disabled={busy || stepIndex(step) === 0}>
          <ArrowLeftIcon aria-hidden />
          Anterior
        </Button>
        {step !== 'revision' ? (
          <Button type="button" className="h-11 px-4" onClick={goNext} disabled={busy}>
            Siguiente
            <ArrowRightIcon aria-hidden />
          </Button>
        ) : null}
        {mode === 'edit' && step !== 'revision' ? (
          <Button type="button" variant="outline" className="h-11 px-4" disabled={busy} onClick={() => void submit('save')}>
            <SaveIcon aria-hidden />
            {label('save', 'Guardar cambios')}
          </Button>
        ) : null}
        {step === 'revision' && mode === 'create' ? (
          <>
            <Button type="button" variant="outline" className="h-11 px-4" disabled={busy} onClick={() => void submit('draft')}>
              {label('draft', 'Guardar borrador')}
            </Button>
            {canPublish ? (
              <Button type="button" className="h-11 px-4" disabled={busy} onClick={() => void submit('publish')}>
                {label('publish', 'Crear y publicar')}
              </Button>
            ) : null}
          </>
        ) : null}
        {step === 'revision' && mode === 'edit' ? (
          <>
            <Button type="button" variant={published || !canPublish ? 'default' : 'outline'} className="h-11 px-4" disabled={busy} onClick={() => void submit('save')}>
              {label('save', 'Guardar cambios')}
            </Button>
            {canPublish && !published ? (
              <Button type="button" className="h-11 px-4" disabled={busy} onClick={() => void submit('publish')}>
                {label('publish', 'Guardar y publicar')}
              </Button>
            ) : null}
            {canPublish && published ? (
              <Button type="button" variant="outline" className="h-11 px-4" disabled={busy} onClick={() => void submit('unpublish')}>
                {label('unpublish', 'Retirar del marketplace')}
              </Button>
            ) : null}
          </>
        ) : null}
        <Button type="button" variant="ghost" className="h-11 px-4 sm:ml-auto" nativeButton={false} render={<Link to={`${base}/productos`} />}>
          Cancelar
        </Button>
      </div>
      <p className="sr-only" role="status">
        Paso {stepIndex(step) + 1} de {STEPS.length}: {stepDef.label}
      </p>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}

function EditHeaderActions({ product, rows }: { product: ProductRow; rows: ProductRow[] }) {
  const { storeId, can, invalidateStore } = usePortal()
  const navigate = useNavigate()
  const { setPublished, busyId } = usePublishProduct()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const published = product.state === 'ACTIVE'
  return (
    <>
      {can('PRODUCT_PUBLISH') ? (
        <Button variant={published ? 'outline' : 'default'} className="h-11 px-4" disabled={busyId === product.id} onClick={() => void setPublished(product, !published)}>
          {published ? <EyeOffIcon aria-hidden /> : <EyeIcon aria-hidden />}
          {published ? 'Retirar del marketplace' : 'Publicar'}
        </Button>
      ) : null}
      {can('PRODUCT_DELETE') ? (
        <Button variant="destructive" className="h-11 px-4" onClick={() => setConfirmDelete(true)}>
          <Trash2Icon aria-hidden />
          Eliminar
        </Button>
      ) : null}
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={`¿Eliminar “${product.name}”?`}
        description={`Dejará de verse en el marketplace y se borrarán sus imágenes y sus existencias${rows.length > 1 ? ' en todas las sucursales' : ''}. Esta acción no se puede deshacer.`}
        confirmLabel="Eliminar producto"
        destructive
        onConfirm={async () => {
          await (await getProductService()).remove(storeId, product.id)
          invalidateStore()
          toast.success(`Eliminaste “${product.name}”.`)
          navigate(`/portal/${encodeURIComponent(storeId)}/productos`, { replace: true })
          focusMain()
        }}
      />
    </>
  )
}

/** `/portal/:storeId/productos/nuevo` y `/productos/:productId`: asistente por secciones con un único envío final. */
export default function PortalProductFormPage() {
  const { productId } = useParams()
  const mode = productId ? 'edit' : 'create'
  const { storeId } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`

  const products = useStoreQuery('products', async (id) => (await getProductService()).list(id))
  const options = useAsync('portal:product-options', async () => (await getProductService()).options())
  const manufacturerQuery = useStoreQuery('manufacturers', async (id) => (await getProductService()).listManufacturers(id))
  const branchQuery = useStoreQuery('branches', async (id) => (await getBranchService()).list(id))

  const crumbs = [
    { label: 'Productos', to: `${base}/productos` },
    { label: mode === 'create' ? 'Nuevo producto' : 'Editar producto' },
  ]

  if (products.status === 'error') return <QueryError error={products.error} what="tus productos" onRetry={products.retry} />
  if (options.status === 'error') return <QueryError error={options.error} what="las opciones del catálogo" onRetry={options.retry} />
  if (manufacturerQuery.status === 'error') return <QueryError error={manufacturerQuery.error} what="los fabricantes" onRetry={manufacturerQuery.retry} />
  if (branchQuery.status === 'error') return <QueryError error={branchQuery.error} what="tus sucursales" onRetry={branchQuery.retry} />
  if (!products.data || !options.data || !manufacturerQuery.data || !branchQuery.data) {
    return <ListSkeleton rows={6} label="Cargando el formulario" />
  }

  const activeBranches = branchQuery.data.filter((branch) => branch.state === 'ACTIVE').map((branch) => ({ value: branch.id, label: branch.name }))
  const rows = productId ? products.data.filter((row) => row.id === productId) : []
  const product = rows[0]

  if (mode === 'edit' && !product) {
    return (
      <EmptyState icon={<PackageSearchIcon />} title="No encontramos ese producto" description="Puede que se haya eliminado o que no sea de este almacén.">
        <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/productos`} />}>
          Volver a productos
        </Button>
      </EmptyState>
    )
  }
  if (mode === 'create' && activeBranches.length === 0) {
    return (
      <EmptyState
        icon={<PackageSearchIcon />}
        title="Primero necesitas una sucursal activa"
        description="Cada producto se guarda en una sucursal con su precio y existencias. Crea o reactiva una sucursal y vuelve."
      >
        <Button className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/sucursales`} />}>
          Ir a sucursales
        </Button>
      </EmptyState>
    )
  }

  const branchOptions = mode === 'edit' ? rows.map((row) => ({ value: row.branchId, label: row.branchName })) : activeBranches

  return (
    <>
      <title>{`${mode === 'create' ? 'Nuevo producto' : (product?.name ?? 'Producto')} | Autopartco`}</title>
      <PageHeader
        crumbs={crumbs}
        title={mode === 'create' ? 'Nuevo producto' : (product?.name ?? 'Producto')}
        description={mode === 'create' ? 'Completa los pasos a tu ritmo. No se guarda nada hasta el final: allí eliges entre borrador o publicar.' : 'Edita por secciones y guarda al terminar. Nada cambia hasta que pulses un botón de guardar.'}
        meta={product ? (product.state === 'ACTIVE' ? <StatusBadge tone="success">Publicado</StatusBadge> : <StatusBadge>Sin publicar</StatusBadge>) : undefined}
        actions={product ? <EditHeaderActions product={product} rows={rows} /> : undefined}
      />
      {/* key: al cambiar de producto o de modo el formulario se reinicia con los datos nuevos. */}
      <ProductForm key={`${mode}:${productId ?? ''}`} mode={mode} rows={rows} options={options.data} manufacturers={manufacturerQuery.data} branches={branchOptions} />
    </>
  )
}
