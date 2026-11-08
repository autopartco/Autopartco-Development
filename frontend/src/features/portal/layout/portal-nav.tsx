import { NavLink } from 'react-router'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { usePortal } from '../context'
import { PORTAL_SECTION_GROUPS } from '../lib/sections'

/**
 * Enlaces del portal sobre grafito (§7.3). El ítem activo lleva fondo tenue, texto blanco y una
 * línea de acento `ap-red-on-dark` (4.6:1 sobre grafito) a la izquierda, además de `aria-current="page"`.
 */
export function PortalNav({ onNavigate }: { onNavigate?: () => void }) {
  const { storeId, can, permissionsReady } = usePortal()

  if (!permissionsReady) {
    return (
      <div className="flex flex-col gap-1.5 px-3" role="status">
        <span className="sr-only">Cargando menú…</span>
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} aria-hidden className="h-10 bg-ap-line-on-dark" />
        ))}
      </div>
    )
  }

  return (
    <nav aria-label="Portal del almacén" className="flex flex-col gap-5 px-3">
      {PORTAL_SECTION_GROUPS.map((group) => {
        const items = group.items.filter((item) => can(item.permission))
        if (items.length === 0) return null
        const headingId = `nav-${group.label}`
        return (
          <div key={group.label} className="flex flex-col gap-1">
            <p id={headingId} className="ap-index px-3 pb-1">
              {group.label}
            </p>
            <ul aria-labelledby={headingId} className="flex flex-col">
              {items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={`/portal/${encodeURIComponent(storeId)}/${item.to}`}
                    end={item.end}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        'relative flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors duration-150 hover:bg-sidebar-accent hover:text-ap-white lg:min-h-10',
                        'before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:bg-ap-red-on-dark before:opacity-0',
                        isActive ? 'bg-sidebar-accent text-ap-white before:opacity-100' : 'text-ap-on-dark-muted',
                      )
                    }
                  >
                    <item.icon className="size-4 shrink-0" aria-hidden />
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </nav>
  )
}
