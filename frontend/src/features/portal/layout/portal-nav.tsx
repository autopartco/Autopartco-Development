import type { ComponentType } from 'react'
import { NavLink } from 'react-router'
import {
  Building2Icon,
  ClipboardListIcon,
  CreditCardIcon,
  LayoutDashboardIcon,
  MapPinIcon,
  PackageIcon,
  UsersIcon,
  type LucideProps,
} from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import type { StorePermission } from '../api/types'
import { usePortal } from '../context'

interface NavItem {
  to: string
  label: string
  icon: ComponentType<LucideProps>
  /** Permiso que hace visible el enlace. El servidor sigue decidiendo cada petición. */
  permission: StorePermission
  /** Coincide también con las rutas hijas (p. ej. `productos/:id`). */
  end?: boolean
}

const GROUPS: { label: string; items: NavItem[] }[] = [
  {
    label: 'Operación',
    items: [
      { to: 'inicio', label: 'Inicio', icon: LayoutDashboardIcon, permission: 'STORE_READ', end: true },
      { to: 'productos', label: 'Productos', icon: PackageIcon, permission: 'PRODUCT_READ' },
      { to: 'pedidos', label: 'Pedidos', icon: ClipboardListIcon, permission: 'ORDER_READ' },
    ],
  },
  {
    label: 'Administración',
    items: [
      { to: 'sucursales', label: 'Sucursales', icon: MapPinIcon, permission: 'BRANCH_READ' },
      { to: 'equipo', label: 'Equipo', icon: UsersIcon, permission: 'COLLABORATOR_READ' },
      { to: 'tienda', label: 'Tienda', icon: Building2Icon, permission: 'STORE_READ' },
      { to: 'plan', label: 'Plan y suscripción', icon: CreditCardIcon, permission: 'STORE_READ' },
    ],
  },
]

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
      {GROUPS.map((group) => {
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
