import { XIcon } from 'lucide-react'
import type { InterpretedQuery } from '../../api/types'
import type { InterpretedChip } from '../../lib/search-query'

interface Chip {
  key: InterpretedChip
  label: string
  /** Para lectores de pantalla: qué entendimos. */
  kind: string
}

function buildChips(interpreted: InterpretedQuery): Chip[] {
  const chips: Chip[] = []
  if (interpreted.makeId && interpreted.makeName) chips.push({ key: 'make', label: interpreted.makeName, kind: 'Marca' })
  if (interpreted.modelId && interpreted.modelName) chips.push({ key: 'model', label: interpreted.modelName, kind: 'Modelo' })
  if (interpreted.year) chips.push({ key: 'year', label: String(interpreted.year), kind: 'Año' })
  if (interpreted.cityCode) chips.push({ key: 'city', label: interpreted.cityName ?? interpreted.cityCode, kind: 'Ciudad' })
  if (interpreted.reference) chips.push({ key: 'reference', label: interpreted.reference, kind: 'Referencia' })
  return chips
}

interface InterpretedChipsProps {
  interpreted: InterpretedQuery | undefined
  onRemove: (chip: InterpretedChip) => void
}

/** Lo que entendimos del texto, como fichas que se pueden quitar para buscar sin esa interpretación. */
export function InterpretedChips({ interpreted, onRemove }: InterpretedChipsProps) {
  const chips = interpreted ? buildChips(interpreted) : []
  if (chips.length === 0) return null
  return (
    <div className="flex flex-wrap items-center gap-2">
      <p className="ap-index mr-1">Entendimos</p>
      <ul className="contents">
        {chips.map((chip) => (
          <li key={chip.key}>
            <button
              type="button"
              onClick={() => onRemove(chip.key)}
              aria-label={`Quitar ${chip.kind.toLowerCase()}: ${chip.label}`}
              className="inline-flex h-9 items-center gap-1.5 rounded-sm border border-ap-graphite bg-ap-paper pr-2 pl-2.5 text-sm text-ap-ink transition-colors hover:bg-ap-white pointer-coarse:h-11"
            >
              <span className="text-xs text-ap-muted">{chip.kind}</span>
              <span className="font-medium">{chip.label}</span>
              <XIcon className="size-3.5 text-ap-muted" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
