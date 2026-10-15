import type { ComponentType } from 'react'
import {
  Building2Icon,
  ClipboardListIcon,
  CreditCardIcon,
  LayoutDashboardIcon,
  LineChartIcon,
  MapPinIcon,
  PackageIcon,
  UsersIcon,
  WarehouseIcon,
  type LucideProps,
} from 'lucide-react'
import type { StorePermission } from '../api/types'

/** Secciones del portal (segmento de ruta tras `/portal/:storeId/`). */
export const PORTAL_SECTIONS = ['inicio', 'ventas', 'productos', 'inventario', 'pedidos', 'sucursales', 'equipo', 'tienda', 'plan'] as const

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
  /** Palabras extra para la búsqueda de la paleta de comandos. */
  keywords?: string
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
      { to: 'inicio', label: 'Inicio', icon: LayoutDashboardIcon, permission: 'STORE_READ', end: true, keywords: 'resumen dashboard' },
      { to: 'ventas', label: 'Ventas', icon: LineChartIcon, permission: 'ORDER_READ', keywords: 'ingresos analitica metricas' },
      { to: 'productos', label: 'Productos', icon: PackageIcon, permission: 'PRODUCT_READ', keywords: 'catalogo repuestos' },
      { to: 'inventario', label: 'Inventario', icon: WarehouseIcon, permission: 'PRODUCT_READ', keywords: 'stock existencias' },
      { to: 'pedidos', label: 'Pedidos', icon: ClipboardListIcon, permission: 'ORDER_READ', keywords: 'ventas envios' },
    ],
  },
  {
    label: 'Administración',
    items: [
      { to: 'sucursales', label: 'Sucursales', icon: MapPinIcon, permission: 'BRANCH_READ', keywords: 'sedes bodegas' },
      { to: 'equipo', label: 'Equipo', icon: UsersIcon, permission: 'COLLABORATOR_READ', keywords: 'empleados colaboradores' },
      { to: 'tienda', label: 'Tienda', icon: Building2Icon, permission: 'STORE_READ', keywords: 'perfil almacen' },
      { to: 'plan', label: 'Plan y suscripción', icon: CreditCardIcon, permission: 'STORE_READ', keywords: 'pago facturacion' },
    ],
  },
]

/** Alias usado por la paleta de comandos. */
export const PORTAL_NAV_GROUPS = PORTAL_SECTION_GROUPS

/** Primera sección a la que la persona tiene acceso, o `undefined` si no tiene ninguna. */
export function firstAllowedSection(
  can: (permission: StorePermission) => boolean,
  storeId: string,
): string | undefined {
  const first = PORTAL_SECTION_GROUPS.flatMap((group) => group.items).find((item) => can(item.permission))
  return first ? `/portal/${encodeURIComponent(storeId)}/${first.to}` : undefined
}
