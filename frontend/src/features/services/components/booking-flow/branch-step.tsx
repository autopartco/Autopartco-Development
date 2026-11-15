import { useId } from 'react'
import { MapPinIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { StoreBranch } from '@/features/marketplace/api/types'
import { cn } from '@/lib/utils'

interface BranchStepProps {
  /** Solo las sedes que prestan el servicio. */
  branches: StoreBranch[]
  value: string | undefined
  onChange: (branchId: string) => void
}

/** Paso «Sede»: una opción por sede con su dirección; se elige con teclado (flechas) o toque. */
export function BranchStep({ branches, value, onChange }: BranchStepProps) {
  const name = useId()
  return (
    <fieldset className="flex min-w-0 flex-col gap-2">
      <legend className="sr-only">Sede donde quieres la cita</legend>
      {branches.map((branch) => {
        const checked = branch.id === value
        const place = [branch.address, branch.city].filter(Boolean).join(', ')
        return (
          <label
            key={branch.id}
            className={cn(
              'flex min-h-11 cursor-pointer items-start gap-3 rounded-md border bg-ap-white p-3 transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--focus)]',
              checked ? 'border-ap-graphite bg-ap-paper' : 'border-ap-line hover:border-ap-graphite',
            )}
          >
            <input type="radio" name={name} value={branch.id} checked={checked} onChange={() => onChange(branch.id)} className="mt-1 size-4 shrink-0 accent-[var(--ap-red)]" />
            <span className="flex min-w-0 flex-1 flex-col gap-1">
              <span className="font-semibold break-words text-ap-ink">{branch.name}</span>
              {place ? (
                <span className="flex items-start gap-1.5 text-sm break-words text-ap-muted">
                  <MapPinIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                  {place}
                </span>
              ) : null}
            </span>
            {branch.openNow !== undefined ? <Badge variant={branch.openNow ? 'success' : 'neutral'}>{branch.openNow ? 'Abierta ahora' : 'Cerrada ahora'}</Badge> : null}
          </label>
        )
      })}
    </fieldset>
  )
}
