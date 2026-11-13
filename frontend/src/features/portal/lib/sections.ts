import type { ComponentType } from 'react'
import {
  BellIcon,
  BrainCircuitIcon,
  Building2Icon,
  ClipboardListIcon,
  CreditCardIcon,
  LayoutDashboardIcon,
  LineChartIcon,
  MegaphoneIcon,
  MessageCircleQuestionIcon,
  MapPinIcon,
  PackageIcon,
  StarIcon,
  TagIcon,
  UsersIcon,
  WarehouseIcon,
  type LucideProps,
} from 'lucide-react'
import type { StorePermission } from '../api/types'

/** Secciones del portal (segmento de ruta tras `/portal/:storeId/`). */
export const PORTAL_SECTIONS = ['inicio', 'ventas', 'productos', 'inventario', 'inteligencia', 'pedidos', 'promociones', 'publicidad', 'alertas', 'resenas', 'preguntas', 'sucursales', 'equipo', 'tienda', 'plan'] as const

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
  /** Permiso (o permisos: basta uno) que da acceso a la sección. El servidor sigue decidiendo cada petición. */
  permission: StorePermission | readonly StorePermission[]
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
      { to: 'inteligencia', label: 'Inteligencia', icon: BrainCircuitIcon, permission: 'ANALYTICS_READ', keywords: 'reportes clientes segmentos proyeccion abc analitica' },
      { to: 'pedidos', label: 'Pedidos', icon: ClipboardListIcon, permission: 'ORDER_READ', keywords: 'ventas envios' },
      { to: 'promociones', label: 'Promociones', icon: TagIcon, permission: 'PROMOTION_READ', keywords: 'descuentos ofertas audiencia resultados' },
      { to: 'publicidad', label: 'Publicidad', icon: MegaphoneIcon, permission: ['STORE_UPDATE', 'ANALYTICS_READ'], keywords: 'anuncios campanas patrocinado destacados' },
      { to: 'alertas', label: 'Alertas', icon: BellIcon, permission: 'STORE_READ', keywords: 'avisos notificaciones agotado caida ventas preferencias' },
      { to: 'resenas', label: 'Reseñas', icon: StarIcon, permission: 'STORE_READ', keywords: 'opiniones calificaciones estrellas responder comentarios clientes' },
      { to: 'preguntas', label: 'Preguntas', icon: MessageCircleQuestionIcon, permission: 'STORE_READ', keywords: 'dudas consultas compradores responder producto' },
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

/** `true` si la cuenta tiene alguno de los permisos de la sección. */
export function canOpenSection(can: (permission: StorePermission) => boolean, section: Pick<PortalSection, 'permission'>): boolean {
  const required = section.permission
  return typeof required === 'string' ? can(required) : required.some((permission) => can(permission))
}

/** Sección del portal por su segmento de ruta. */
export function findSection(to: string): PortalSection | undefined {
  return PORTAL_SECTION_GROUPS.flatMap((group) => group.items).find((item) => item.to === to)
}

/** Primera sección a la que la persona tiene acceso, o `undefined` si no tiene ninguna. */
export function firstAllowedSection(
  can: (permission: StorePermission) => boolean,
  storeId: string,
): string | undefined {
  const first = PORTAL_SECTION_GROUPS.flatMap((group) => group.items).find((item) => canOpenSection(can, item))
  return first ? `/portal/${encodeURIComponent(storeId)}/${first.to}` : undefined
}
