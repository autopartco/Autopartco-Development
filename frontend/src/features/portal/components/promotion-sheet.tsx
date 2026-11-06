import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { DiscardChangesDialog } from '@/components/common/discard-changes-dialog'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { ApiError } from '@/lib/api'
import { useAsync } from '@/lib/use-async'
import { useCloseGuard } from '@/lib/use-close-guard'
import { useDraftForm, type FieldErrors } from '@/lib/use-draft-form'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import type {
  Promotion,
  PromotionAttributeOperator,
  PromotionAudience,
  PromotionDiscountType,
  PromotionInput,
  PromotionTargetType,
} from '../api/promotion-types'
import { actionErrorMessage } from '../lib/errors'
import { focusById, parseAmount } from '../lib/forms'
import type { PromotionConfig } from '../lib/promotion-config'
import { AUDIENCE_HINT, AUDIENCE_LABEL, fromLocalInput, TARGET_LABEL, toLocalInput } from '../lib/promotions'
import { SelectField } from './form-controls'

const FIELDS = ['name', 'audience', 'targetType', 'targetId', 'attributeKey', 'attributeValue', 'discountValue', 'startsAt', 'endsAt'] as const
const fieldId = (field: string) => `promocion-${field}`

interface Values {
  name: string
  audience: PromotionAudience
  targetType: PromotionTargetType
  targetId: string
  attributeKey: string
  attributeValue: string
  attributeOperator: PromotionAttributeOperator
  discountType: PromotionDiscountType
  discountValue: string
  startsAt: string
  endsAt: string
  enabled: boolean
}

const NEEDS_ID: PromotionTargetType[] = ['PRODUCT', 'CATEGORY', 'MANUFACTURER']

function valuesOf(promotion: Promotion | undefined, config: PromotionConfig, audience: PromotionAudience | undefined): Values {
  if (promotion) {
    return {
      name: promotion.name,
      audience: promotion.audience,
      targetType: promotion.targetType,
      targetId: promotion.targetId ?? '',
      attributeKey: promotion.attributeKey ?? '',
      attributeValue: promotion.attributeValue ?? '',
      attributeOperator: promotion.attributeOperator ?? 'EQUALS',
      discountType: promotion.discountType,
      discountValue: String(promotion.discountValue).replace('.', ','),
      startsAt: toLocalInput(promotion.startsAt),
      endsAt: toLocalInput(promotion.endsAt),
      enabled: promotion.enabled,
    }
  }
  const start = new Date()
  start.setSeconds(0, 0)
  const end = new Date(start.getTime() + 30 * 86_400_000)
  return {
    name: '',
    audience: audience && config.audiences.includes(audience) ? audience : 'ALL',
    targetType: config.targetTypes[0] ?? 'STORE',
    targetId: '',
    attributeKey: '',
    attributeValue: '',
    attributeOperator: 'EQUALS',
    discountType: 'PERCENTAGE',
    discountValue: '',
    startsAt: toLocalInput(start.toISOString()),
    endsAt: toLocalInput(end.toISOString()),
    enabled: true,
  }
}

function makeValidate(config: PromotionConfig) {
  return (values: Values): FieldErrors => {
    const errors: FieldErrors = {}
    if (!values.name.trim()) errors.name = 'Ponle un nombre a la promoción.'
    else if (values.name.trim().length > 120) errors.name = 'El nombre no puede superar 120 caracteres.'
    if (values.audience === 'PLATFORM_FREQUENT' && !config.platformAudienceAllowed) {
      errors.audience = 'Tu plan no incluye Reportes avanzados. Elige otra audiencia.'
    }
    if (NEEDS_ID.includes(values.targetType) && !values.targetId) errors.targetId = 'Elige a qué aplica el descuento.'
    if (values.targetType === 'ATTRIBUTE') {
      if (!values.attributeKey.trim()) errors.attributeKey = 'Escribe el nombre del atributo.'
      if (!values.attributeValue.trim()) errors.attributeValue = 'Escribe el valor del atributo.'
    }
    const amount = parseAmount(values.discountValue)
    if (amount === undefined || amount <= 0) errors.discountValue = 'Escribe un descuento mayor que cero.'
    else if (values.discountType === 'PERCENTAGE' && amount > 100) errors.discountValue = 'El porcentaje debe estar entre 0 y 100.'
    const start = fromLocalInput(values.startsAt)
    const end = fromLocalInput(values.endsAt)
    if (!start) errors.startsAt = 'Elige cuándo empieza.'
    if (!end) errors.endsAt = 'Elige cuándo termina.'
    else if (start && Date.parse(end) <= Date.parse(start)) errors.endsAt = 'La fecha final debe ser posterior a la de inicio.'
    return errors
  }
}

function buildInput(values: Values): PromotionInput {
  const attribute = values.targetType === 'ATTRIBUTE'
  return {
    name: values.name.trim(),
    targetType: values.targetType,
    targetId: NEEDS_ID.includes(values.targetType) ? values.targetId : null,
    attributeKey: attribute ? values.attributeKey.trim() : null,
    attributeValue: attribute ? values.attributeValue.trim() : null,
    attributeOperator: attribute ? values.attributeOperator : null,
    discountType: values.discountType,
    discountValue: parseAmount(values.discountValue) ?? 0,
    startsAt: fromLocalInput(values.startsAt) ?? '',
    endsAt: fromLocalInput(values.endsAt) ?? '',
    enabled: values.enabled,
    audience: values.audience,
  }
}

interface PromotionFormProps {
  config: PromotionConfig
  promotion: Promotion | undefined
  initialAudience: PromotionAudience | undefined
  onDone: () => void
  onCancel: () => void
  onDirtyChange: (dirty: boolean) => void
}

function PromotionForm({ config, promotion, initialAudience, onDone, onCancel, onDirtyChange }: PromotionFormProps) {
  const options = useAsync(`${config.cacheKey}:targets`, config.loadTargetOptions)
  const draft = useDraftForm<Values>({ initial: valuesOf(promotion, config, initialAudience), validate: makeValidate(config) })
  const { values } = draft
  const submit = useSubmitOnce(async (input: PromotionInput) => {
    if (promotion) await config.service.update(config.storeId, promotion.id, input)
    else await config.service.create(config.storeId, input)
  })
  const pending = submit.pending
  const blocker = useUnsavedChanges(draft.isDirty && !pending)
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    onDirtyChange(draft.isDirty && !pending)
    return () => onDirtyChange(false)
  }, [draft.isDirty, pending, onDirtyChange])

  const bind = (field: keyof Values & string) => ({
    id: fieldId(field),
    error: draft.errorFor(field),
    onBlur: () => draft.touch(field),
  })
  const text = (field: 'name' | 'attributeKey' | 'attributeValue' | 'discountValue' | 'startsAt' | 'endsAt') => ({
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
    // ÚNICA escritura: crear o actualizar al pulsar el botón final.
    const result = await submit.run(buildInput(values))
    if (result.status === 'ok') {
      draft.markSaved()
      onDirtyChange(false)
      config.invalidate()
      toast.success(promotion ? 'Promoción actualizada.' : 'Promoción creada.')
      onDone()
    } else if (result.status === 'error') {
      const error = result.error
      if (error instanceof ApiError && error.code === 'SELLER_PLAN_FEATURE_UNAVAILABLE' && values.audience === 'PLATFORM_FREQUENT') {
        draft.setServerErrors({ audience: 'Tu plan no incluye Reportes avanzados. Elige otra audiencia.' })
        config.promptUpgrade?.(error)
        requestAnimationFrame(() => focusById(fieldId('audience')))
        return
      }
      if (config.promptUpgrade?.(error)) return
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        draft.setServerErrors(error.fieldErrors)
        const first = FIELDS.find((field) => error.fieldErrors[field])
        if (first) requestAnimationFrame(() => focusById(fieldId(first)))
      }
      setFormError(actionErrorMessage(error, 'No pudimos guardar la promoción. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  const targetTypes = promotion && !config.targetTypes.includes(promotion.targetType) ? [...config.targetTypes, promotion.targetType] : config.targetTypes
  const idOptions =
    values.targetType === 'PRODUCT'
      ? options.data?.products
      : values.targetType === 'CATEGORY'
        ? options.data?.categories
        : values.targetType === 'MANUFACTURER'
          ? options.data?.manufacturers
          : undefined
  const idLabel = values.targetType === 'PRODUCT' ? 'Producto' : values.targetType === 'CATEGORY' ? 'Categoría' : 'Marca'
  const audienceBlocked = (audience: PromotionAudience) => audience === 'PLATFORM_FREQUENT' && !config.platformAudienceAllowed

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex min-h-0 flex-1 flex-col" aria-busy={pending || undefined}>
      <div className="flex flex-1 flex-col gap-4 overflow-y-auto px-4 pb-4">
        {formError ? (
          <FormAlert ref={alertRef} title="No se guardó la promoción">
            {formError}
          </FormAlert>
        ) : null}
        <TextField {...text('name')} label="Nombre" maxLength={120} autoComplete="off" required hint="Lo verán tus clientes junto al precio. Ej.: Semana del mecánico." />

        <SelectField
          {...bind('audience')}
          label="¿Quién ve este precio?"
          value={values.audience}
          onChange={(event) => draft.set('audience', event.target.value as PromotionAudience)}
          options={config.audiences.map((audience) => ({
            value: audience,
            label: audienceBlocked(audience) ? `${AUDIENCE_LABEL[audience]} (requiere Reportes avanzados)` : AUDIENCE_LABEL[audience],
            disabled: audienceBlocked(audience),
          }))}
          hint={
            <>
              {AUDIENCE_HINT[values.audience]}
              {!config.platformAudienceAllowed && config.audiences.includes('PLATFORM_FREQUENT') ? (
                <>
                  {' '}
                  «Clientes frecuentes de Autopartco» no está disponible porque tu plan no incluye Reportes avanzados.
                  {config.plansPath ? (
                    <>
                      {' '}
                      <Link to={config.plansPath} className="font-semibold underline underline-offset-4">
                        Ver planes
                      </Link>
                      .
                    </>
                  ) : null}
                </>
              ) : null}
            </>
          }
        />

        <SelectField
          {...bind('targetType')}
          label="¿A qué se aplica?"
          value={values.targetType}
          onChange={(event) => draft.patch({ targetType: event.target.value as PromotionTargetType, targetId: '' })}
          options={targetTypes.map((type) => ({ value: type, label: TARGET_LABEL[type] }))}
        />
        {NEEDS_ID.includes(values.targetType) ? (
          <SelectField
            {...bind('targetId')}
            label={idLabel}
            value={values.targetId}
            onChange={(event) => draft.set('targetId', event.target.value)}
            placeholder={idOptions ? 'Elige una opción' : 'Cargando opciones…'}
            options={[
              ...(idOptions ?? []).map((option) => ({ value: option.id, label: option.label })),
              // Destino que ya no aparece en la lista (p. ej. producto retirado): se conserva para poder editar el resto.
              ...(values.targetId && !(idOptions ?? []).some((option) => option.id === values.targetId) ? [{ value: values.targetId, label: 'Destino actual' }] : []),
            ]}
            disabled={idOptions === undefined && options.status !== 'error'}
            hint={options.status === 'error' ? 'No pudimos cargar las opciones. Cierra el panel y vuelve a abrirlo.' : undefined}
          />
        ) : null}
        {values.targetType === 'ATTRIBUTE' ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField {...text('attributeKey')} label="Atributo" maxLength={100} autoComplete="off" required hint="Ej.: Posición." />
            <SelectField
              id={fieldId('attributeOperator')}
              label="Condición"
              value={values.attributeOperator}
              onChange={(event) => draft.set('attributeOperator', event.target.value as PromotionAttributeOperator)}
              options={[
                { value: 'EQUALS', label: 'Es igual a' },
                { value: 'CONTAINS', label: 'Contiene' },
              ]}
            />
            <TextField {...text('attributeValue')} label="Valor" maxLength={255} autoComplete="off" required containerClassName="sm:col-span-2" hint="Ej.: Delantera." />
          </div>
        ) : null}

        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            id={fieldId('discountType')}
            label="Tipo de descuento"
            value={values.discountType}
            onChange={(event) => draft.set('discountType', event.target.value as PromotionDiscountType)}
            options={[
              { value: 'PERCENTAGE', label: 'Porcentaje (%)' },
              { value: 'FIXED_AMOUNT', label: 'Monto fijo (COP)' },
            ]}
          />
          <TextField
            {...text('discountValue')}
            label={values.discountType === 'PERCENTAGE' ? 'Descuento (%)' : 'Descuento (COP)'}
            inputMode="decimal"
            autoComplete="off"
            required
            hint={values.discountType === 'PERCENTAGE' ? 'Entre 1 y 100.' : 'Se descuenta de cada unidad.'}
          />
          <TextField {...text('startsAt')} type="datetime-local" label="Empieza" required />
          <TextField {...text('endsAt')} type="datetime-local" label="Termina" required />
        </div>

        <label className="flex min-h-11 cursor-pointer items-start gap-3 rounded-lg bg-concrete p-3 text-sm">
          <Checkbox checked={values.enabled} onCheckedChange={(checked) => draft.set('enabled', checked === true)} className="mt-0.5" />
          <span>
            <span className="block font-semibold">Promoción activa</span>
            <span className="text-muted-foreground">Si la pausas, el descuento no se aplica aunque esté dentro de las fechas.</span>
          </span>
        </label>
      </div>
      <div className="flex flex-col-reverse gap-2 border-t border-border p-4 sm:flex-row sm:justify-end">
        <Button type="button" variant="outline" className="h-11 px-5" onClick={onCancel} disabled={pending}>
          Cancelar
        </Button>
        <Button type="submit" className="h-11 px-5" disabled={pending}>
          {pending ? 'Guardando…' : promotion ? 'Guardar cambios' : 'Crear promoción'}
        </Button>
      </div>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}

interface PromotionSheetProps {
  config: PromotionConfig
  open: boolean
  onOpenChange: (open: boolean) => void
  promotion: Promotion | undefined
  /** Audiencia con la que abre una promoción nueva (p. ej. desde un segmento de clientes). */
  initialAudience?: PromotionAudience
}

/** Alta y edición de una promoción en un panel lateral (`Sheet`). */
export function PromotionSheet({ config, open, onOpenChange, promotion, initialAudience }: PromotionSheetProps) {
  const guard = useCloseGuard(() => onOpenChange(false))
  return (
    <Sheet open={open} onOpenChange={(next) => (next ? onOpenChange(true) : guard.requestClose())}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-lg">
        <SheetHeader className="p-4 pr-16">
          <SheetTitle className="text-lg font-extrabold tracking-tight">{promotion ? 'Editar promoción' : 'Nueva promoción'}</SheetTitle>
          <SheetDescription>
            {promotion ? 'Cambia el descuento, las fechas o a quién se muestra.' : 'Define el descuento, cuándo corre y quién lo ve.'}
          </SheetDescription>
        </SheetHeader>
        {/* key: cada apertura (o cambio de promoción) parte de un formulario limpio. */}
        <PromotionForm
          key={promotion?.id ?? `nueva-${initialAudience ?? ''}`}
          config={config}
          promotion={promotion}
          initialAudience={initialAudience}
          onDone={() => onOpenChange(false)}
          onCancel={guard.requestClose}
          onDirtyChange={guard.setDirty}
        />
        <DiscardChangesDialog guard={guard} />
      </SheetContent>
    </Sheet>
  )
}
