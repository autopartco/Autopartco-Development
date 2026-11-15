import { PlusIcon, Trash2Icon } from 'lucide-react'
import { INPUT_CLASS, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { DAY_LABELS, WEEK_DAYS, type WeekDay } from '@/features/marketplace/lib/store-links'
import { cn } from '@/lib/utils'
import {
  copyMondayToWeekdays,
  DEFAULT_SLOT,
  hoursDayId,
  hoursNotesId,
  MAX_SLOTS,
  NOTES_MAX,
  type HoursDraft,
  type HoursErrors,
} from '../lib/opening-hours'

interface OpeningHoursEditorProps {
  idPrefix: string
  value: HoursDraft
  onChange: (next: HoursDraft) => void
  /** Errores del cliente; se muestran solo tras intentar guardar. */
  errors: HoursErrors | undefined
  /** Error que devolvió el servidor (`INVALID_OPENING_HOURS`). */
  serverError?: string
}

/**
 * «Horario de atención»: por día, casilla «Abierto», hasta dos franjas y notas. Un día sin marcar queda cerrado; sin
 * ningún día abierto la sede no publica horario semanal y no puede recibir citas en línea.
 */
export function OpeningHoursEditor({ idPrefix, value, onChange, errors, serverError }: OpeningHoursEditorProps) {
  function setDay(day: WeekDay, next: HoursDraft['days'][WeekDay]) {
    onChange({ ...value, days: { ...value.days, [day]: next } })
  }

  return (
    <fieldset className="flex min-w-0 flex-col gap-3" aria-describedby={`${idPrefix}-ayuda`}>
      <legend className="text-sm font-semibold text-ap-ink">Horario de atención</legend>
      <p id={`${idPrefix}-ayuda`} className="-mt-1 text-xs text-ap-muted">
        Marca los días que abres y escribe la hora de apertura y de cierre. Sin horario, los compradores no podrán agendar citas en esta sede.
      </p>
      <div>
        <Button type="button" variant="outline" size="sm" className="h-11 px-3" onClick={() => onChange(copyMondayToWeekdays(value))}>
          Copiar lunes a días hábiles
        </Button>
      </div>

      <div className="flex flex-col gap-2">
        {WEEK_DAYS.map((day) => {
          const state = value.days[day]
          const error = errors?.days[day]
          const errorId = `${hoursDayId(idPrefix, day)}-error`
          return (
            <div key={day} role="group" aria-labelledby={`${hoursDayId(idPrefix, day)}-nombre`} className={cn('flex flex-col gap-2 rounded-md border p-3', error ? 'border-ap-danger' : 'border-ap-line')}>
              <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
                <Checkbox
                  id={hoursDayId(idPrefix, day)}
                  checked={state.open}
                  aria-describedby={error ? errorId : undefined}
                  onCheckedChange={(checked) => setDay(day, checked === true ? { open: true, slots: state.slots.length > 0 ? state.slots : [{ ...DEFAULT_SLOT }] } : { ...state, open: false })}
                />
                <span id={`${hoursDayId(idPrefix, day)}-nombre`} className="font-semibold text-ap-ink">
                  {DAY_LABELS[day]}
                </span>
                <span className="text-ap-muted">{state.open ? 'Abierto' : 'Cerrado'}</span>
              </label>
              {state.open ? (
                <div className="flex flex-col gap-2">
                  {state.slots.map((slot, index) => {
                    const slotId = `${hoursDayId(idPrefix, day)}-${index}`
                    const update = (changes: Partial<typeof slot>) => setDay(day, { ...state, slots: state.slots.map((item, at) => (at === index ? { ...item, ...changes } : item)) })
                    return (
                      <div key={index} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] items-end gap-2">
                        <div className="flex min-w-0 flex-col gap-1">
                          <label htmlFor={`${slotId}-abre`} className="text-xs font-semibold text-ap-ink">
                            Abre{state.slots.length > 1 ? ` (franja ${index + 1})` : ''}
                          </label>
                          <input id={`${slotId}-abre`} type="time" value={slot.from} onChange={(event) => update({ from: event.target.value })} aria-invalid={error ? true : undefined} className={INPUT_CLASS} />
                        </div>
                        <div className="flex min-w-0 flex-col gap-1">
                          <label htmlFor={`${slotId}-cierra`} className="text-xs font-semibold text-ap-ink">
                            Cierra{state.slots.length > 1 ? ` (franja ${index + 1})` : ''}
                          </label>
                          <input id={`${slotId}-cierra`} type="time" value={slot.to} onChange={(event) => update({ to: event.target.value })} aria-invalid={error ? true : undefined} className={INPUT_CLASS} />
                        </div>
                        {state.slots.length > 1 ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="size-11"
                            aria-label={`Quitar la franja ${index + 1} del ${DAY_LABELS[day].toLowerCase()}`}
                            onClick={() => setDay(day, { ...state, slots: state.slots.filter((_, at) => at !== index) })}
                          >
                            <Trash2Icon className="size-4" aria-hidden />
                          </Button>
                        ) : (
                          <span aria-hidden className="size-11" />
                        )}
                      </div>
                    )
                  })}
                  {state.slots.length < MAX_SLOTS ? (
                    <div>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-11 px-3"
                        onClick={() => {
                          const last = state.slots.at(-1)
                          setDay(day, { ...state, slots: [...state.slots, { from: last && last.to < '23:00' ? last.to : '14:00', to: '18:00' }] })
                        }}
                      >
                        <PlusIcon aria-hidden />
                        Añadir otra franja
                      </Button>
                    </div>
                  ) : null}
                </div>
              ) : null}
              {error ? (
                <p id={errorId} role="alert" className="text-sm font-medium text-ap-danger">
                  {error}
                </p>
              ) : null}
            </div>
          )
        })}
      </div>

      <TextField
        id={hoursNotesId(idPrefix)}
        label="Notas del horario"
        optional
        maxLength={NOTES_MAX + 20}
        autoComplete="off"
        value={value.notes}
        onChange={(event) => onChange({ ...value, notes: event.target.value })}
        error={errors?.notes}
        hint={`${value.notes.trim().length} de ${NOTES_MAX} caracteres. Ej.: Festivos de 9:00 a 13:00.`}
      />
      {serverError ? (
        <p role="alert" className="text-sm font-medium text-ap-danger">
          {serverError}
        </p>
      ) : null}
    </fieldset>
  )
}
