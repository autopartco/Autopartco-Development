import { NavLink } from 'react-router'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { usePortal } from '../context'
import { PORTAL_SECTION_GROUPS } from '../lib/sections'

/** Enlaces del portal sobre fondo carbón. El ítem activo lleva barra roja de 2px y `aria-current="page"`. */
export function PortalNav({ onNavigate }: { onNavigate?: () => void }) {
  const { storeId, can, permissionsReady } = usePortal()

  if (!permissionsReady) {
    return (
      <div className="flex flex-col gap-2 px-3" role="status">
        <span className="sr-only">Cargando menú…</span>
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} aria-hidden className="h-10 bg-asphalt-line" />
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
            <p id={headingId} className="label-mono px-3 pb-1 text-asphalt-muted">
              {group.label}
            </p>
            <ul aria-labelledby={headingId} className="flex flex-col gap-0.5">
              {items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={`/portal/${encodeURIComponent(storeId)}/${item.to}`}
                    end={item.end}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        'relative flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors hover:bg-header-hover hover:text-white lg:min-h-10',
                        'before:absolute before:inset-y-2 before:left-0 before:w-0.5 before:rounded-full before:bg-thread-on-asphalt before:opacity-0 before:transition-opacity',
                        isActive ? 'bg-header-hover text-white before:opacity-100' : 'text-asphalt-muted',
                      )
                    }
                  >
                    <item.icon className="size-[1.125rem] shrink-0" aria-hidden />
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
