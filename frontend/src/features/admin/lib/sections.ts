import type { ComponentType } from 'react'
import {
  ActivityIcon,
  BadgePercentIcon,
  BrainCircuitIcon,
  ShieldUserIcon,
  StoreIcon,
  ReceiptTextIcon,
  WalletIcon,
  UserCogIcon,
  BarChart3Icon,
  ClipboardCheckIcon,
  CreditCardIcon,
  LayoutDashboardIcon,
  MegaphoneIcon,
  ScrollTextIcon,
  MessageCircleQuestionIcon,
  StarIcon,
  SearchIcon,
  TagsIcon,
  UsersIcon,
  type LucideProps,
} from 'lucide-react'
import type { SectionAccess } from './access'

export interface AdminSection {
  /** Segmento bajo `/admin/`. */
  to: string
  label: string
  icon: ComponentType<LucideProps>
  /** Una línea para el resumen. */
  description: string
  /** Permiso que exige la sección (el servidor lo vuelve a comprobar). */
  access: SectionAccess
}

export interface AdminSectionGroup {
  label: string
  items: AdminSection[]
}

/** Menú de la consola, agrupado como el del portal de almacenes. Cada cuenta ve solo las secciones de sus permisos. */
export const ADMIN_SECTION_GROUPS: AdminSectionGroup[] = [
  {
    label: 'Plataforma',
    items: [
      { to: 'resumen', label: 'Resumen', icon: LayoutDashboardIcon, description: 'Estado general y pendientes.', access: 'PLATFORM_DASHBOARD_READ' },
      { to: 'solicitudes', label: 'Solicitudes', icon: ClipboardCheckIcon, description: 'Verifica almacenes nuevos.', access: 'PLATFORM_APPLICATIONS_REVIEW' },
      { to: 'almacenes', label: 'Almacenes', icon: StoreIcon, description: 'Plan, actividad y equipo de cada almacén.', access: 'PLATFORM_STORES_READ' },
      { to: 'cuentas', label: 'Cuentas', icon: ShieldUserIcon, description: 'Usuarios, sesiones y bloqueos.', access: 'PLATFORM_ACCOUNTS_READ' },
      { to: 'auditoria', label: 'Auditoría', icon: ScrollTextIcon, description: 'Historial de ingresos y cambios sensibles.', access: 'PLATFORM_AUDIT_READ' },
      { to: 'equipo', label: 'Equipo', icon: UserCogIcon, description: 'Cuentas de administración y soporte, con sus roles.', access: 'ROOT' },
    ],
  },
  {
    label: 'Operación',
    items: [
      { to: 'pedidos', label: 'Pedidos', icon: ReceiptTextIcon, description: 'Pedidos de todos los almacenes con su detalle.', access: 'PLATFORM_ORDERS_READ' },
      { to: 'pagos', label: 'Pagos', icon: WalletIcon, description: 'Cobros, rechazos y reembolsos.', access: 'PLATFORM_ORDERS_READ' },
      { to: 'pagos-atipicos', label: 'Pagos atípicos', icon: ActivityIcon, description: 'Pagos que el modelo marcó para revisión.', access: 'PLATFORM_PAYMENT_RISK_REVIEW' },
    ],
  },
  {
    label: 'Comercial',
    items: [
      { to: 'planes', label: 'Planes', icon: CreditCardIcon, description: 'Precios, límites y funciones de cada plan.', access: 'PLATFORM_PLANS_MANAGE' },
      { to: 'promociones', label: 'Promociones', icon: BadgePercentIcon, description: 'Descuentos para todo el marketplace.', access: 'PLATFORM_PROMOTIONS_MANAGE' },
      { to: 'publicidad', label: 'Publicidad', icon: MegaphoneIcon, description: 'Campañas de anuncios y tarifas por ubicación.', access: 'PLATFORM_ADS_MANAGE' },
      { to: 'catalogo', label: 'Catálogo', icon: TagsIcon, description: 'Categorías y fabricantes oficiales.', access: 'PLATFORM_CATALOG_MANAGE' },
      { to: 'resenas', label: 'Reseñas', icon: StarIcon, description: 'Moderación de opiniones de compradores.', access: 'PLATFORM_MODERATION' },
      { to: 'preguntas', label: 'Preguntas', icon: MessageCircleQuestionIcon, description: 'Moderación de preguntas sobre productos.', access: 'PLATFORM_MODERATION' },
    ],
  },
  {
    label: 'Inteligencia',
    items: [
      { to: 'estadisticas', label: 'Estadísticas', icon: BarChart3Icon, description: 'Tráfico, cuentas, accesos y comercio.', access: 'PLATFORM_ANALYTICS_READ' },
      { to: 'clientes', label: 'Clientes', icon: UsersIcon, description: 'Segmentos de compradores con consentimiento.', access: 'PLATFORM_ANALYTICS_READ' },
      { to: 'busquedas', label: 'Búsquedas', icon: SearchIcon, description: 'Demanda y búsquedas sin resultado.', access: 'PLATFORM_ANALYTICS_READ' },
      { to: 'modelos', label: 'Modelos', icon: BrainCircuitIcon, description: 'Modelos predictivos, métricas y ejecuciones.', access: 'PLATFORM_ANALYTICS_READ' },
    ],
  },
]

/** Secciones visibles para una cuenta, sin grupos vacíos. */
export function visibleSectionGroups(can: (access: SectionAccess) => boolean): AdminSectionGroup[] {
  return ADMIN_SECTION_GROUPS.map((group) => ({ ...group, items: group.items.filter((item) => can(item.access)) })).filter(
    (group) => group.items.length > 0,
  )
}

export function sectionOf(segment: string): AdminSection | undefined {
  return ADMIN_SECTION_GROUPS.flatMap((group) => group.items).find((item) => item.to === segment)
}
