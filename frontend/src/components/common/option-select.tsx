import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

export interface SelectOption {
  value: string
  label: string
}

interface OptionSelectProps {
  id: string
  label: string
  /** Valor vacío (`''`) significa "sin selección" y muestra `emptyLabel`. */
  value: string
  emptyLabel: string
  options: SelectOption[]
  onValueChange: (value: string) => void
  disabled?: boolean
  hideLabel?: boolean
  className?: string
  triggerClassName?: string
}

/** Select accesible (Base UI) con etiqueta visible y opción "cualquiera". */
export function OptionSelect({
  id,
  label,
  value,
  emptyLabel,
  options,
  onValueChange,
  disabled,
  hideLabel,
  className,
  triggerClassName,
}: OptionSelectProps) {
  const items = [{ value: '', label: emptyLabel }, ...options]
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', className)}>
      <Label htmlFor={id} className={cn('text-sm font-medium', hideLabel && 'sr-only')}>
        {label}
      </Label>
      <Select
        items={items}
        value={value}
        disabled={disabled}
        onValueChange={(next) => onValueChange(typeof next === 'string' ? next : '')}
      >
        <SelectTrigger id={id} className={cn('h-10 w-full bg-card pointer-coarse:h-11', triggerClassName)}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {items.map((item) => (
            <SelectItem key={item.value || '__empty'} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
