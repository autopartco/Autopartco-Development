import { useId } from 'react'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { cn } from '@/lib/utils'

interface SearchFieldProps {
  label?: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  className?: string
}

/** Campo de búsqueda con etiqueta visible, usado por todas las listas del portal. */
export function SearchField({ label = 'Buscar', value, onChange, placeholder, className }: SearchFieldProps) {
  const id = useId()
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
      </label>
      <input
        id={id}
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className={INPUT_CLASS}
        autoComplete="off"
      />
    </div>
  )
}

/** Resumen "12 de 40 pedidos" que se anuncia a lectores de pantalla al filtrar. */
export function ResultCount({ shown, total, singular, plural }: { shown: number; total: number; singular: string; plural: string }) {
  const noun = total === 1 ? singular : plural
  return (
    <p role="status" className="text-sm text-muted-foreground">
      {shown === total ? `${total} ${noun}` : `${shown} de ${total} ${plural}`}
    </p>
  )
}
