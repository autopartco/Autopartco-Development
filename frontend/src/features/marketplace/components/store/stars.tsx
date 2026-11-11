import { StarIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Estrellas de solo lectura; el valor va en texto para lectores de pantalla. */
export function Stars({ value, className }: { value: number; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-0.5', className)} role="img" aria-label={`${value} de 5`}>
      {Array.from({ length: 5 }, (_, index) => (
        <StarIcon
          key={index}
          aria-hidden
          className={cn('size-4', index < Math.round(value) ? 'fill-ap-graphite text-ap-graphite' : 'text-ap-line-strong')}
        />
      ))}
    </span>
  )
}
