import { useId, useRef, useState } from 'react'
import { CircleAlertIcon, PlusIcon, RotateCwIcon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useAsync, type AsyncResult } from '@/lib/use-async'
import { getProductService } from '../api/product-service'
import type { VehicleRule } from '../api/types'
import { addRule, MAX_VEHICLE_RULES, ruleLabel } from '../lib/vehicle-rules'
import { SelectField } from './form-controls'

interface VehicleRulesPickerProps {
  id: string
  /** Reglas elegidas (con nombres). El estado vive en el formulario padre. */
  value: VehicleRule[]
  onChange: (rules: VehicleRule[]) => void
  /** Error general (p. ej. «Elige al menos un vehículo»). */
  error?: string
  /** Errores del servidor por posición de regla (`VEHICLE_RULE_INVALID`). */
  ruleErrors?: Record<number, string>
  onBlur?: () => void
  disabled?: boolean
}

const NONE_KEY = 'portal:vehicles:none'
const sortByName = <T extends { name: string }>(items: T[]) => items.toSorted((a, b) => a.name.localeCompare(b.name, 'es'))

/** Mensaje y reintento de una lista que no cargó. */
function LoadError({ what, result }: { what: string; result: AsyncResult<unknown> }) {
  return (
    <div role="alert" className="flex flex-wrap items-center gap-2 rounded-lg border border-destructive/25 bg-destructive/5 p-3 text-sm text-destructive">
      <CircleAlertIcon className="size-4 shrink-0" aria-hidden />
      <span className="font-medium">No pudimos cargar {what}.</span>
      <Button type="button" variant="outline" size="sm" className="h-9 px-3" onClick={result.retry}>
        <RotateCwIcon aria-hidden />
        Reintentar
      </Button>
    </div>
  )
}

/**
 * Selector de reglas de compatibilidad: Marca → Modelo → Versión con «todos» en los dos últimos
 * niveles, varias reglas por producto y chips legibles. Cada nivel se carga al elegir el anterior y
 * `useAsync` guarda las listas en memoria (el catálogo es cacheable), así que repetir una marca no
 * vuelve a pedirla. Las opciones dependen del nivel superior, por lo que no hay combinaciones
 * incoherentes. Solo lee del servidor; las reglas viajan al guardar el producto.
 */
export function VehicleRulesPicker({ id, value, onChange, error, ruleErrors, onBlur, disabled }: VehicleRulesPickerProps) {
  const [makeId, setMakeId] = useState('')
  const [modelId, setModelId] = useState('')
  const [versionId, setVersionId] = useState('')
  const [notice, setNotice] = useState('')
  const makeRef = useRef<HTMLSelectElement>(null)
  const listId = useId()
  const errorId = `${id}-error`

  const makes = useAsync('portal:vehicles:makes', async () => (await getProductService()).listMakes())
  const models = useAsync(makeId ? `portal:vehicles:models:${makeId}` : NONE_KEY, async () =>
    makeId ? (await getProductService()).listModels(makeId) : [],
  )
  const versions = useAsync(modelId ? `portal:vehicles:versions:${modelId}` : NONE_KEY, async () =>
    modelId ? (await getProductService()).listVersions(modelId) : [],
  )

  const make = makes.data?.find((item) => item.id === makeId)
  const model = models.data?.find((item) => item.id === modelId)
  const version = versions.data?.find((item) => item.id === versionId)
  const modelsLoading = Boolean(makeId) && models.status === 'loading'
  const versionsLoading = Boolean(modelId) && versions.status === 'loading'
  const full = value.length >= MAX_VEHICLE_RULES

  function pickMake(next: string) {
    setMakeId(next)
    setModelId('')
    setVersionId('')
    setNotice('')
  }

  function pickModel(next: string) {
    setModelId(next)
    setVersionId('')
    setNotice('')
  }

  function add() {
    if (!make || full) return
    const rule: VehicleRule = {
      makeId: make.id,
      makeName: make.name,
      modelId: model?.id ?? null,
      modelName: model?.name ?? null,
      versionId: model && version ? version.id : null,
      versionLabel: model && version ? version.label : null,
    }
    const result = addRule(value, rule)
    if (result.status === 'covered') {
      setNotice(`Ya está incluida en «${ruleLabel(result.by)}».`)
      return
    }
    onChange(result.rules)
    const message =
      result.replaced.length > 0
        ? `Agregaste «${ruleLabel(rule)}» y reemplazó ${result.replaced.length === 1 ? 'una regla más específica' : `${result.replaced.length} reglas más específicas`}.`
        : `Agregaste «${ruleLabel(rule)}».`
    pickMake('')
    setNotice(message)
    makeRef.current?.focus()
  }

  function remove(index: number) {
    const rule = value[index]
    if (!rule) return
    onChange(value.filter((_, position) => position !== index))
    setNotice(`Quitaste «${ruleLabel(rule)}».`)
    makeRef.current?.focus()
  }

  return (
    <fieldset
      id={id}
      tabIndex={-1}
      aria-invalid={error ? true : undefined}
      aria-describedby={error ? errorId : undefined}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) onBlur?.()
      }}
      className="flex min-w-0 flex-col gap-3 outline-none"
      disabled={disabled}
    >
      <legend className="mb-1.5 text-sm font-semibold">Vehículos compatibles</legend>
      <p className="text-xs text-muted-foreground">
        Elige la marca y, si quieres, el modelo y la versión. Sin modelo o versión, la regla cubre todos. Puedes agregar varias reglas.
      </p>

      {makes.status === 'error' ? <LoadError what="las marcas" result={makes} /> : null}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_auto] lg:items-end">
        <SelectField
          ref={makeRef}
          id={`${id}-marca`}
          label="Marca"
          placeholder={makes.status === 'loading' && !makes.data ? 'Cargando marcas…' : 'Elige una marca'}
          options={sortByName(makes.data ?? []).map((item) => ({ value: item.id, label: item.name }))}
          value={makeId}
          onChange={(event) => pickMake(event.target.value)}
          disabled={!makes.data}
        />
        <SelectField
          id={`${id}-modelo`}
          label="Modelo"
          placeholder={modelsLoading ? 'Cargando modelos…' : 'Todos los modelos'}
          options={sortByName(makeId && models.data ? models.data : []).map((item) => ({ value: item.id, label: item.name }))}
          value={modelId}
          onChange={(event) => pickModel(event.target.value)}
          disabled={!makeId || modelsLoading || models.status === 'error'}
        />
        <SelectField
          id={`${id}-version`}
          label="Versión"
          placeholder={versionsLoading ? 'Cargando versiones…' : 'Todas las versiones'}
          options={(modelId && versions.data ? versions.data : []).map((item) => ({ value: item.id, label: item.label }))}
          value={versionId}
          onChange={(event) => setVersionId(event.target.value)}
          disabled={!modelId || versionsLoading || versions.status === 'error'}
        />
        <Button type="button" className="h-11 px-4" onClick={add} disabled={!make || full}>
          <PlusIcon aria-hidden />
          Agregar
        </Button>
      </div>
      {makeId && models.status === 'error' ? <LoadError what="los modelos" result={models} /> : null}
      {modelId && versions.status === 'error' ? <LoadError what="las versiones" result={versions} /> : null}
      {full ? <p className="text-xs text-muted-foreground">Llegaste al máximo de {MAX_VEHICLE_RULES} reglas por producto.</p> : null}

      <p className="min-h-4 text-xs text-muted-foreground" role="status" aria-live="polite">
        {notice}
      </p>

      <div className={cn('rounded-lg border bg-card p-3', error ? 'border-destructive' : 'border-input')}>
        <p id={listId} className="mb-2 text-sm font-semibold">
          Reglas agregadas <span className="font-normal text-muted-foreground">({value.length})</span>
        </p>
        {value.length === 0 ? (
          <p className="text-sm text-muted-foreground">Aún no agregas ningún vehículo. Elige una marca y pulsa «Agregar».</p>
        ) : (
          <ul aria-labelledby={listId} className="flex flex-wrap gap-2">
            {value.map((rule, index) => {
              const label = ruleLabel(rule)
              const problem = ruleErrors?.[index]
              return (
                <li key={`${rule.makeId}:${rule.modelId ?? ''}:${rule.versionId ?? ''}`} className="flex max-w-full flex-col gap-1">
                  <span
                    className={cn(
                      'inline-flex min-h-10 max-w-full items-center gap-1 rounded-full bg-concrete pr-1 pl-3 text-sm',
                      problem && 'ring-1 ring-destructive',
                    )}
                  >
                    <span className="min-w-0 truncate">{label}</span>
                    <button
                      type="button"
                      onClick={() => remove(index)}
                      aria-label={`Quitar regla ${label}`}
                      className="inline-grid size-8 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-foreground/10 hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring pointer-coarse:size-9"
                    >
                      <XIcon className="size-4" aria-hidden />
                    </button>
                  </span>
                  {problem ? <span className="px-3 text-xs font-medium text-destructive">{problem}</span> : null}
                </li>
              )
            })}
          </ul>
        )}
      </div>

      {error ? (
        <p id={errorId} className="flex items-start gap-1.5 text-sm font-medium text-destructive">
          <CircleAlertIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
          {error}
        </p>
      ) : null}
    </fieldset>
  )
}
