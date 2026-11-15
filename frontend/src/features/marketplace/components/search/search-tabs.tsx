import { Link, useSearchParams } from 'react-router'
import { cn } from '@/lib/utils'
import { searchTabHref, type SearchTab } from '../../lib/search-query'

const TABS: { tab: SearchTab; label: string }[] = [
  { tab: 'all', label: 'Todo' },
  { tab: 'parts', label: 'Repuestos' },
  { tab: 'services', label: 'Servicios' },
  { tab: 'stores', label: 'Tiendas' },
]

/**
 * Pestañas «Todo · Repuestos · Servicios · Tiendas» de `/buscar?tab=`. Son enlaces reales (compartibles, con atrás/adelante);
 * la activa lleva la línea de acento y `aria-current`.
 */
export function SearchTabs({ active }: { active: SearchTab }) {
  const [searchParams] = useSearchParams()
  return (
    <nav aria-label="Qué buscar" className="mb-5 border-b border-ap-line">
      <ul className="-mb-px flex gap-1 overflow-x-auto">
        {TABS.map(({ tab, label }) => {
          const selected = tab === active
          return (
            <li key={tab}>
              <Link
                to={searchTabHref(searchParams, active, tab)}
                aria-current={selected ? 'page' : undefined}
                className={cn(
                  'inline-flex min-h-11 items-center border-b-2 px-4 text-sm font-semibold whitespace-nowrap transition-colors',
                  selected ? 'border-ap-red text-ap-ink' : 'border-transparent text-ap-muted hover:border-ap-line-strong hover:text-ap-ink',
                )}
              >
                {label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
