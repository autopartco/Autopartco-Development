import { useLayoutEffect, useRef } from 'react'
import { FieldShell, INPUT_CLASS } from '@/components/common/form-fields'
import { cn } from '@/lib/utils'

const copNumber = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 })

/** Solo dígitos, sin ceros a la izquierda y con tope de 10 cifras (el servidor acepta menos de 1e10). */
const digitsOf = (value: string): string => value.replace(/\D/g, '').replace(/^0+(?=\d)/, '').slice(0, 10)

/** «85000» → «85.000». El valor que se envía sale de los dígitos (`Number(digits)`), no del texto mostrado. */
const formatDigits = (digits: string): string => (digits ? copNumber.format(Number(digits)) : '')

interface CopFieldProps {
  id: string
  label: string
  /** Dígitos sin formato. */
  value: string
  onChange: (digits: string) => void
  onBlur?: () => void
  hint?: string
  error?: string
  required?: boolean
  disabled?: boolean
}

/** Campo de precio en pesos colombianos con máscara de miles; conserva la posición del cursor al reformatear. */
export function CopField({ id, label, value, onChange, onBlur, hint, error, required, disabled }: CopFieldProps) {
  const ref = useRef<HTMLInputElement>(null)
  const digitsBeforeCaret = useRef<number | null>(null)

  useLayoutEffect(() => {
    const input = ref.current
    const target = digitsBeforeCaret.current
    digitsBeforeCaret.current = null
    if (!input || target === null || document.activeElement !== input) return
    let seen = 0
    let position = 0
    while (position < input.value.length && seen < target) {
      if (/\d/.test(input.value.charAt(position))) seen += 1
      position += 1
    }
    input.setSelectionRange(position, position)
  }, [value])

  return (
    <FieldShell id={id} label={label} hint={hint} error={error}>
      {(describedBy) => (
        <div className="relative">
          <span aria-hidden className="pointer-events-none absolute inset-y-0 left-3 grid place-items-center text-sm text-muted-foreground">
            $
          </span>
          <input
            ref={ref}
            id={id}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            value={formatDigits(value)}
            required={required}
            disabled={disabled}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy}
            placeholder="0"
            className={cn(INPUT_CLASS, 'pr-14 pl-7 tabular-nums')}
            onBlur={onBlur}
            onChange={(event) => {
              const raw = event.target.value
              const caret = event.target.selectionStart ?? raw.length
              digitsBeforeCaret.current = raw.slice(0, caret).replace(/\D/g, '').length
              onChange(digitsOf(raw))
            }}
          />
          <span aria-hidden className="pointer-events-none absolute inset-y-0 right-3 grid place-items-center text-xs font-semibold text-muted-foreground">
            COP
          </span>
        </div>
      )}
    </FieldShell>
  )
}
