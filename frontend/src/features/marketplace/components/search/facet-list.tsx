import { useState } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Button } from '@/components/ui/button'
import { formatNumber } from '@/lib/format'

export interface FacetItem {
  id: string
  name: string
  /** Resultados con esta opción (el servidor excluye el propio filtro). Sin valor: no se muestra. */
  count?: number
}

const ROW =
  'flex min-h-9 cursor-pointer items-center gap-3 rounded-md px-1 text-sm hover:bg-ap-paper pointer-coarse:min-h-11'
const COLLAPSED_ITEMS = 6

function Count({ value }: { value: number | undefined }) {
  if (value === undefined) return null
  return (
    <span className="ml-auto pl-2 text-xs text-ap-muted tabular-nums">
      <span className="sr-only">, </span>
      {formatNumber(value)}
      <span className="sr-only"> resultados</span>
    </span>
  )
}

function useCollapsed(total: number, selectedBeyond: boolean) {
  const [expanded, setExpanded] = useState(false)
  const collapsible = total > COLLAPSED_ITEMS
  return { visibleAll: expanded || !collapsible || selectedBeyond, collapsible, expanded, toggle: () => setExpanded((v) => !v) }
}

interface MultiProps {
  label: string
  items: FacetItem[]
  selected: string[]
  onChange: (selected: string[]) => void
}

/** Facetas de selección múltiple (fabricantes): casillas con conteo, plegadas tras las primeras opciones. */
export function MultiFacetList({ label, items, selected, onChange }: MultiProps) {
  //Las opciones elegidas siempre se ven, aunque no estén entre las primeras.
  const selectedBeyond = items.slice(COLLAPSED_ITEMS).some((item) => selected.includes(item.id))
  const { visibleAll, collapsible, expanded, toggle } = useCollapsed(items.length, selectedBeyond)
  const shown = visibleAll ? items : items.slice(0, COLLAPSED_ITEMS)
  return (
    <div className="flex flex-col" role="group" aria-label={label}>
      {shown.map((item) => (
        <label key={item.id} className={ROW}>
          <Checkbox
            checked={selected.includes(item.id)}
            onCheckedChange={(checked) =>
              onChange(checked === true ? [...selected, item.id] : selected.filter((value) => value !== item.id))
            }
          />
          <span className="min-w-0 flex-1 truncate">{item.name}</span>
          <Count value={item.count} />
        </label>
      ))}
      {collapsible && !selectedBeyond ? (
        <Button variant="link" className="h-9 w-fit px-1 pointer-coarse:h-11" aria-expanded={expanded} onClick={toggle}>
          {expanded ? 'Ver menos' : `Ver las ${items.length}`}
        </Button>
      ) : null}
    </div>
  )
}

interface SingleProps {
  label: string
  allLabel: string
  items: FacetItem[]
  selected: string | undefined
  onChange: (selected: string | undefined) => void
}

/** Facetas de selección única (categoría, ciudad): opciones de radio con conteo. */
export function SingleFacetList({ label, allLabel, items, selected, onChange }: SingleProps) {
  const selectedBeyond = items.slice(COLLAPSED_ITEMS).some((item) => item.id === selected)
  const { visibleAll, collapsible, expanded, toggle } = useCollapsed(items.length, selectedBeyond)
  const shown = visibleAll ? items : items.slice(0, COLLAPSED_ITEMS)
  return (
    <div className="flex flex-col">
      <RadioGroup
        aria-label={label}
        value={selected ?? ''}
        onValueChange={(value) => onChange(typeof value === 'string' && value ? value : undefined)}
        className="gap-0"
      >
        {[{ id: '', name: allLabel, count: undefined } as FacetItem, ...shown].map((item) => (
          <label key={item.id || 'all'} className={ROW}>
            <RadioGroupItem value={item.id} />
            <span className="min-w-0 flex-1 truncate">{item.name}</span>
            <Count value={item.count} />
          </label>
        ))}
      </RadioGroup>
      {collapsible && !selectedBeyond ? (
        <Button variant="link" className="h-9 w-fit px-1 pointer-coarse:h-11" aria-expanded={expanded} onClick={toggle}>
          {expanded ? 'Ver menos' : `Ver las ${items.length}`}
        </Button>
      ) : null}
    </div>
  )
}
