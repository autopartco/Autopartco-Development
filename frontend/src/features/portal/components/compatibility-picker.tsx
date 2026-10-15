import { useId, useMemo, useState } from 'react'
import { CircleAlertIcon } from 'lucide-react'
import { Checkbox } from '@/components/ui/checkbox'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { cn } from '@/lib/utils'
import type { ProductOptions } from '../api/types'

interface CompatibilityPickerProps {
  id: string
  options: ProductOptions['compatibilities']
  selected: string[]
  onChange: (ids: string[]) => void
  error?: string
  onBlur?: () => void
  disabled?: boolean
}

const normalize = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

/** Selector de vehículos compatibles: búsqueda, casillas agrupadas por marca y conteo anunciado. */
export function CompatibilityPicker({ id, options, selected, onChange, error, onBlur, disabled }: CompatibilityPickerProps) {
  const [query, setQuery] = useState('')
  const searchId = useId()
  const errorId = `${id}-error`
  const chosen = useMemo(() => new Set(selected), [selected])

  const groups = useMemo(() => {
    const term = normalize(query.trim())
    const matches = options.filter((option) => !term || normalize(`${option.make} ${option.model} ${option.version}`).includes(term))
    const byMake = new Map<string, typeof matches>()
    for (const option of matches) byMake.set(option.make, [...(byMake.get(option.make) ?? []), option])
    return [...byMake.entries()].toSorted(([a], [b]) => a.localeCompare(b, 'es'))
  }, [options, query])

  function toggle(optionId: string, checked: boolean) {
    onChange(checked ? [...selected, optionId] : selected.filter((value) => value !== optionId))
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
      className="flex min-w-0 flex-col gap-2 outline-none"
      disabled={disabled}
    >
      <legend className="mb-1.5 text-sm font-semibold">Vehículos compatibles</legend>
      <label htmlFor={searchId} className="sr-only">
        Buscar vehículo por marca, modelo o versión
      </label>
      <input
        id={searchId}
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Buscar marca, modelo o versión"
        className={INPUT_CLASS}
        autoComplete="off"
      />
      <p className="text-xs text-muted-foreground" aria-live="polite">
        {selected.length === 0 ? 'Ninguno seleccionado.' : `${selected.length} ${selected.length === 1 ? 'vehículo seleccionado' : 'vehículos seleccionados'}.`}
      </p>
      <div className={cn('max-h-72 overflow-y-auto rounded-lg border bg-card p-2', error ? 'border-destructive' : 'border-input')}>
        {groups.length === 0 ? (
          <p className="p-3 text-sm text-muted-foreground">No hay vehículos que coincidan con “{query}”.</p>
        ) : (
          groups.map(([make, items]) => (
            <div key={make} role="group" aria-label={make} className="py-1">
              <p aria-hidden className="label-mono px-2 py-1 text-muted-foreground">
                {make}
              </p>
              {items.map((option) => (
                <label
                  key={option.id}
                  className="flex min-h-10 cursor-pointer items-center gap-3 rounded-md px-2 text-sm hover:bg-concrete pointer-coarse:min-h-11"
                >
                  <Checkbox checked={chosen.has(option.id)} onCheckedChange={(value) => toggle(option.id, value === true)} />
                  <span>
                    {option.make} {option.model} <span className="text-muted-foreground">{option.version}</span>
                  </span>
                </label>
              ))}
            </div>
          ))
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
