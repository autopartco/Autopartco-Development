import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { SearchField } from '@/features/portal/components/search-field'
import { StatusBadge } from '@/features/portal/components/status-badge'
import type { Tone } from '../lib/console-labels'

/** Búsqueda de texto que se aplica al enviar o al salir del campo (no en cada tecla: cada búsqueda va al servidor). */
export function QuerySearch({ label, placeholder, value, onApply }: { label: string; placeholder: string; value: string; onApply: (value: string) => void }) {
  const [draft, setDraft] = useState(value)
  const [applied, setApplied] = useState(value)
  if (value !== applied) {
    setApplied(value)
    setDraft(value)
  }
  return (
    <div onBlur={() => draft.trim() !== value && onApply(draft.trim())} onKeyDown={(event) => event.key === 'Enter' && (event.preventDefault(), onApply(draft.trim()))}>
      <SearchField label={label} value={draft} onChange={setDraft} placeholder={placeholder} />
    </div>
  )
}

/** Línea bajo los filtros: total del servidor y «Quitar filtros» si hay alguno activo. */
export function ListMeta({ total, singular, plural, activeFilters, onClear }: { total?: number; singular: string; plural: string; activeFilters: number; onClear: () => void }) {
  return (
    <div className="mt-4 mb-4 flex min-h-11 flex-wrap items-center justify-between gap-2 border-b border-ap-line pb-3">
      <p className="ap-ref text-ap-muted">
        {total === undefined ? 'Cargando…' : pluralize(total, singular, plural)}
        {activeFilters > 0 ? ` · ${pluralize(activeFilters, 'filtro activo', 'filtros activos')}` : ''}
      </p>
      {activeFilters > 0 ? (
        <Button variant="ghost" className="h-11 px-3 underline underline-offset-4" onClick={onClear}>
          Quitar filtros
        </Button>
      ) : null}
    </div>
  )
}

export function Pill({ value }: { value: { label: string; tone: Tone } }) {
  return <StatusBadge tone={value.tone}>{value.label}</StatusBadge>
}

/** Pares término/valor de un panel de detalle; `mono` para IDs, referencias y direcciones IP. */
export function KeyValues({ items, className }: { items: [string, ReactNode, boolean?][]; className?: string }) {
  return (
    <dl className={cn('grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2', className)}>
      {items.map(([term, value, mono]) => (
        <div key={term} className="flex min-w-0 flex-col gap-0.5 border-b border-ap-line pb-2">
          <dt className="text-ap-muted">{term}</dt>
          <dd className={cn('break-words text-ap-ink', mono && 'ap-ref break-all')}>{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  )
}

/** Subtítulo de bloque dentro de un panel lateral. */
export function SheetBlock({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2 border-t border-ap-line-strong pt-3">
      <h3 className="text-sm font-semibold text-ap-ink">{title}</h3>
      {children}
    </section>
  )
}
