import { forwardRef, useState, type InputHTMLAttributes, type ReactNode } from 'react'
import { CircleAlertIcon, EyeIcon, EyeOffIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Campos de formulario accesibles: etiqueta visible, ayuda y error enlazados con
 * `aria-describedby`, `aria-invalid` y tamaño táctil de 44px.
 */

export const INPUT_CLASS =
  'h-11 w-full min-w-0 rounded-lg border border-input bg-card px-3 text-base text-foreground placeholder:text-muted-foreground transition-colors aria-[invalid=true]:border-destructive sm:text-sm'

interface FieldShellProps {
  id: string
  label: ReactNode
  hint?: ReactNode
  error?: string
  optional?: boolean
  className?: string
  children: (describedBy: string | undefined) => ReactNode
}

export function FieldShell({ id, label, hint, error, optional, className, children }: FieldShellProps) {
  const hintId = hint ? `${id}-ayuda` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [errorId, hintId].filter(Boolean).join(' ') || undefined
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
        {optional ? <span className="ml-1 font-normal text-muted-foreground">(opcional)</span> : null}
      </label>
      {children(describedBy)}
      {error ? (
        <p id={errorId} className="flex items-start gap-1.5 text-sm font-medium text-destructive">
          <CircleAlertIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
          {error}
        </p>
      ) : null}
      {hint ? (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  )
}

type InputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & {
  id: string
  label: ReactNode
  hint?: ReactNode
  error?: string
  optional?: boolean
  containerClassName?: string
}

export const TextField = forwardRef<HTMLInputElement, InputProps>(function TextField(
  { id, label, hint, error, optional, containerClassName, className, ...props },
  ref,
) {
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} optional={optional} className={containerClassName}>
      {(describedBy) => (
        <input
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(INPUT_CLASS, className)}
          {...props}
        />
      )}
    </FieldShell>
  )
})

/** Contraseña con botón para mostrarla u ocultarla (el botón anuncia su estado con `aria-pressed`). */
export function PasswordField({ id, label, hint, error, containerClassName, className, ...props }: InputProps) {
  const [visible, setVisible] = useState(false)
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} className={containerClassName}>
      {(describedBy) => (
        <div className="relative">
          <input
            id={id}
            type={visible ? 'text' : 'password'}
            aria-invalid={error ? true : undefined}
            aria-describedby={describedBy}
            autoCapitalize="none"
            spellCheck={false}
            className={cn(INPUT_CLASS, 'pr-12', className)}
            {...props}
          />
          <button
            type="button"
            onClick={() => setVisible((value) => !value)}
            aria-pressed={visible}
            aria-controls={id}
            aria-label="Mostrar contraseña"
            className="absolute top-0 right-0 grid size-11 place-items-center rounded-lg text-muted-foreground hover:text-foreground focus-visible:-outline-offset-2!"
          >
            {visible ? <EyeOffIcon className="size-5" aria-hidden /> : <EyeIcon className="size-5" aria-hidden />}
          </button>
        </div>
      )}
    </FieldShell>
  )
}

/**
 * Mensaje de error o éxito de todo el formulario. Recibe el foco al aparecer
 * (`tabIndex={-1}`) para que lectores de pantalla y teclado lo encuentren.
 */
export const FormAlert = forwardRef<HTMLDivElement, { tone?: 'error' | 'info'; title: string; children?: ReactNode }>(
  function FormAlert({ tone = 'error', title, children }, ref) {
    return (
      <div
        ref={ref}
        tabIndex={-1}
        role={tone === 'error' ? 'alert' : 'status'}
        className={cn(
          'flex flex-col gap-1 rounded-lg p-4 text-sm outline-none',
          tone === 'error' ? 'border border-destructive/25 bg-destructive/5' : 'bg-concrete',
        )}
      >
        <p className={cn('font-semibold', tone === 'error' && 'text-destructive')}>{title}</p>
        {children ? <div className="text-foreground/85">{children}</div> : null}
      </div>
    )
  },
)
