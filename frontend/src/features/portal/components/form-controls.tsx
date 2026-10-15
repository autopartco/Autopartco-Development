import { forwardRef, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { FieldShell, INPUT_CLASS } from '@/components/common/form-fields'
import { cn } from '@/lib/utils'

type TextAreaProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> & {
  id: string
  label: ReactNode
  hint?: ReactNode
  error?: string
  optional?: boolean
  containerClassName?: string
}

/** Área de texto con etiqueta visible, ayuda y error enlazados (`aria-describedby`, `aria-invalid`). */
export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextAreaField(
  { id, label, hint, error, optional, containerClassName, className, ...props },
  ref,
) {
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} optional={optional} className={containerClassName}>
      {(describedBy) => (
        <textarea
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(INPUT_CLASS, 'h-auto min-h-24 py-2.5 leading-normal', className)}
          {...props}
        />
      )}
    </FieldShell>
  )
})

type SelectProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> & {
  id: string
  label: ReactNode
  hint?: ReactNode
  error?: string
  optional?: boolean
  containerClassName?: string
  /** Texto de la opción vacía; sin él no hay opción vacía. */
  placeholder?: string
  options: { value: string; label: string }[]
}

/** Lista desplegable nativa (accesible y cómoda en móvil) con la misma anatomía que `TextField`. */
export const SelectField = forwardRef<HTMLSelectElement, SelectProps>(function SelectField(
  { id, label, hint, error, optional, containerClassName, className, placeholder, options, ...props },
  ref,
) {
  return (
    <FieldShell id={id} label={label} hint={hint} error={error} optional={optional} className={containerClassName}>
      {(describedBy) => (
        <select
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(INPUT_CLASS, 'pr-8', className)}
          {...props}
        >
          {placeholder !== undefined ? <option value="">{placeholder}</option> : null}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}
    </FieldShell>
  )
})
