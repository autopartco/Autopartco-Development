import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { SearchSort } from '../../api/types'
import { SEARCH_SORT_LABELS } from '../../lib/search-query'

const ITEMS = (Object.keys(SEARCH_SORT_LABELS) as SearchSort[]).map((value) => ({ value, label: SEARCH_SORT_LABELS[value] }))

interface SortSelectProps {
  value: SearchSort
  onChange: (value: SearchSort) => void
}

/** Orden de los resultados. «Más cercanos» pide la ubicación si aún no se tiene. */
export function SortSelect({ value, onChange }: SortSelectProps) {
  return (
    <div className="flex items-center gap-2">
      <Label htmlFor="orden-resultados" className="hidden text-sm font-normal text-ap-muted sm:block">
        Orden
      </Label>
      <Select items={ITEMS} value={value} onValueChange={(next) => next && onChange(next as SearchSort)}>
        <SelectTrigger id="orden-resultados" aria-label="Ordenar por" className="h-11 min-w-40 bg-ap-white">
          <SelectValue />
        </SelectTrigger>
        <SelectContent align="end">
          {ITEMS.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
