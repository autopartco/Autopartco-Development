import { useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { ApiError } from '@/lib/api'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { useDraftForm, type FieldErrors } from '@/lib/use-draft-form'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import { getAlertService } from '../api/alert-service'
import type { AlertPreference, AlertType } from '../api/alert-types'
import { usePortal } from '../context'
import { ALERT_TYPE_DESCRIPTION, ALERT_TYPE_LABEL } from '../lib/alerts'
import { actionErrorMessage } from '../lib/errors'
import { focusById, parseCount } from '../lib/forms'
import { QueryBoundary } from './query-boundary'

interface ThresholdSpec {
  key: 'ratio' | 'days'
  label: string
  hint: string
  min: number
  max: number
  /** Valor guardado -> texto del campo. */
  toField: (value: number) => string
  /** Texto validado -> valor que espera el servidor. */
  toServer: (value: number) => number
}

const THRESHOLDS: Partial<Record<AlertType, ThresholdSpec>> = {
  SALES_DROP: {
    key: 'ratio',
    label: 'Avisar si las ventas de la semana bajan de (% de tu promedio)',
    hint: 'Entre 10 y 95. Con 70, te avisamos cuando vendas menos del 70 % de lo habitual.',
    min: 10,
    max: 95,
    toField: (value) => String(Math.round(value * 100)),
    toServer: (value) => value / 100,
  },
  STOCKOUT_SOON: {
    key: 'days',
    label: 'Avisar si el producto se agota en menos de (días)',
    hint: 'Entre 1 y 30 días.',
    min: 1,
    max: 30,
    toField: String,
    toServer: (value) => value,
  },
  DEAD_STOCK: {
    key: 'days',
    label: 'Avisar por productos sin ventas durante (días)',
    hint: 'Entre 30 y 365 días.',
    min: 30,
    max: 365,
    toField: String,
    toServer: (value) => value,
  },
}

interface Row {
  type: AlertType
  inApp: boolean
  email: boolean
  threshold: string
}

interface Values {
  rows: Row[]
}

const fieldName = (type: AlertType) => `umbral-${type}`
const fieldId = (name: string) => `alerta-${name}`

function toValues(preferences: AlertPreference[]): Values {
  return {
    rows: preferences.map((preference) => {
      const spec = THRESHOLDS[preference.type]
      const raw = spec ? preference.threshold?.[spec.key] : undefined
      return { type: preference.type, inApp: preference.inApp, email: preference.email, threshold: spec && raw !== undefined ? spec.toField(raw) : '' }
    }),
  }
}

function validate(values: Values): FieldErrors {
  const errors: FieldErrors = {}
  for (const row of values.rows) {
    const spec = THRESHOLDS[row.type]
    if (!spec) continue
    const value = parseCount(row.threshold)
    if (value === undefined || value < spec.min || value > spec.max) {
      errors[fieldName(row.type)] = `Escribe un número entero entre ${spec.min} y ${spec.max}.`
    }
  }
  return errors
}

function PreferencesForm({ preferences }: { preferences: AlertPreference[] }) {
  const { storeId } = usePortal()
  const draft = useDraftForm<Values>({ initial: toValues(preferences), validate })
  const { values } = draft
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)
  const submit = useSubmitOnce(async (payload: AlertPreference[]) => (await getAlertService()).savePreferences(storeId, payload))
  const blocker = useUnsavedChanges(draft.isDirty && !submit.pending)

  function updateRow(type: AlertType, change: Partial<Row>) {
    draft.set('rows', values.rows.map((row) => (row.type === type ? { ...row, ...change } : row)))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(undefined)
    const check = draft.validateAll()
    if (!check.valid) {
      focusById(check.firstInvalid ? fieldId(check.firstInvalid) : undefined)
      return
    }
    const payload: AlertPreference[] = values.rows.map((row) => {
      const spec = THRESHOLDS[row.type]
      const parsed = spec ? parseCount(row.threshold) : undefined
      return {
        type: row.type,
        inApp: row.inApp,
        email: row.email,
        threshold: spec && parsed !== undefined ? { [spec.key]: spec.toServer(parsed) } : null,
      }
    })
    // ÚNICA escritura: guardar la lista completa al pulsar el botón.
    const result = await submit.run(payload)
    if (result.status === 'ok') {
      draft.reset(toValues(result.value))
      invalidateAsync(`portal:${storeId}:alerts:preferences`)
      toast.success('Guardamos tus preferencias de alertas.')
    } else if (result.status === 'error') {
      const error = result.error
      if (error instanceof ApiError && (error.code === 'INVALID_ALERT_THRESHOLD' || error.code === 'INVALID_ALERT_TYPE')) {
        setFormError(error.message)
      } else {
        setFormError(actionErrorMessage(error, 'No pudimos guardar las preferencias. Inténtalo de nuevo.'))
      }
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-5" aria-busy={submit.pending || undefined}>
      {formError ? (
        <FormAlert ref={alertRef} title="No se guardaron las preferencias">
          {formError}
        </FormAlert>
      ) : null}
      <p className="max-w-prose text-sm text-muted-foreground">
        Elige cómo quieres enterarte de cada alerta. Los correos llegan a las personas del equipo que pueden configurar alertas.
      </p>
      <ul className="flex flex-col gap-4">
        {values.rows.map((row) => {
          const spec = THRESHOLDS[row.type]
          const name = fieldName(row.type)
          return (
            <li key={row.type} className="flex flex-col gap-3 rounded-lg bg-card p-4 ring-1 ring-foreground/10">
              <div>
                <h3 className="text-base font-semibold tracking-tight">{ALERT_TYPE_LABEL[row.type]}</h3>
                <p className="text-sm text-muted-foreground">{ALERT_TYPE_DESCRIPTION[row.type]}</p>
              </div>
              <div className="flex flex-wrap gap-x-6 gap-y-1">
                <label className="flex min-h-11 cursor-pointer items-center gap-2.5 text-sm">
                  <Checkbox checked={row.inApp} onCheckedChange={(checked) => updateRow(row.type, { inApp: checked === true })} />
                  En el portal (campana)
                </label>
                <label className="flex min-h-11 cursor-pointer items-center gap-2.5 text-sm">
                  <Checkbox checked={row.email} onCheckedChange={(checked) => updateRow(row.type, { email: checked === true })} />
                  Por correo
                </label>
              </div>
              {spec ? (
                <TextField
                  id={fieldId(name)}
                  label={spec.label}
                  inputMode="numeric"
                  autoComplete="off"
                  value={row.threshold}
                  error={draft.errors[name]}
                  hint={spec.hint}
                  containerClassName="max-w-sm"
                  onChange={(event) => updateRow(row.type, { threshold: event.target.value })}
                />
              ) : null}
            </li>
          )
        })}
      </ul>
      <div className="flex justify-end">
        <Button type="submit" className="h-11 px-5" disabled={submit.pending || !draft.isDirty}>
          {submit.pending ? 'Guardando…' : 'Guardar preferencias'}
        </Button>
      </div>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}

/** Preferencias de alertas por tipo: canales y umbrales. Solo con `ALERT_MANAGE`. */
export function AlertPreferences() {
  const { storeId } = usePortal()
  const preferences = useAsync(`portal:${storeId}:alerts:preferences`, async () => (await getAlertService()).preferences(storeId))
  return (
    <QueryBoundary result={preferences} what="tus preferencias de alertas">
      {(data) => <PreferencesForm key={data.map((item) => item.type).join()} preferences={data} />}
    </QueryBoundary>
  )
}
