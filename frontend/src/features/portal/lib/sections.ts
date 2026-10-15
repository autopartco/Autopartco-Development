import type { ComponentType } from 'react'
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
import type { StorePermission } from '../api/types'

/** Secciones del portal (segmento de ruta tras `/portal/:storeId/`). */
export const PORTAL_SECTIONS = ['inicio', 'productos', 'pedidos', 'sucursales', 'equipo', 'tienda', 'plan'] as const

/** Sección actual a partir de la URL, para conservarla al cambiar de tienda. */
export function sectionOf(pathname: string): string {
  const section = pathname.split('/').filter(Boolean)[2]
  return PORTAL_SECTIONS.find((candidate) => candidate === section) ?? 'inicio'
}

export interface PortalSection {
  /** Segmento de la URL bajo `/portal/:storeId/`. */
  to: string
  label: string
  icon: ComponentType<LucideProps>
  /** Permiso que da acceso a la sección. El servidor sigue decidiendo cada petición. */
  permission: StorePermission
  /** Coincide también con las rutas hijas (p. ej. `productos/:id`). */
  end?: boolean
}

export interface PortalSectionGroup {
  label: string
  items: PortalSection[]
}

/** Secciones del portal agrupadas como aparecen en el menú. El orden define la sección por defecto. */
export const PORTAL_SECTION_GROUPS: PortalSectionGroup[] = [
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

/** Primera sección a la que la persona tiene acceso, o `undefined` si no tiene ninguna. */
export function firstAllowedSection(
  can: (permission: StorePermission) => boolean,
  storeId: string,
): string | undefined {
  const first = PORTAL_SECTION_GROUPS.flatMap((group) => group.items).find((item) => can(item.permission))
  return first ? `/portal/${encodeURIComponent(storeId)}/${first.to}` : undefined
}
