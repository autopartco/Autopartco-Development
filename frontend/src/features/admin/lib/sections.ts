import type { ComponentType } from 'react'
import {
  BadgePercentIcon,
  BarChart3Icon,
  ClipboardCheckIcon,
  CreditCardIcon,
  LayoutDashboardIcon,
  ScrollTextIcon,
  SearchIcon,
  TagsIcon,
  UsersIcon,
  type LucideProps,
} from 'lucide-react'

export interface AdminSection {
  /** Segmento bajo `/admin/`. */
  to: string
  label: string
  icon: ComponentType<LucideProps>
  /** Una línea para el resumen. */
  description: string
}

export interface AdminSectionGroup {
  label: string
  items: AdminSection[]
}

/** Menú de la consola ROOT, agrupado como el del portal de almacenes. */
export const ADMIN_SECTION_GROUPS: AdminSectionGroup[] = [
  {
    label: 'Plataforma',
    items: [
      { to: 'resumen', label: 'Resumen', icon: LayoutDashboardIcon, description: 'Estado general y pendientes.' },
      { to: 'solicitudes', label: 'Solicitudes', icon: ClipboardCheckIcon, description: 'Verifica almacenes nuevos.' },
      { to: 'auditoria', label: 'Auditoría', icon: ScrollTextIcon, description: 'Historial de ingresos y cambios sensibles.' },
    ],
  },
  {
    label: 'Comercial',
    items: [
      { to: 'planes', label: 'Planes', icon: CreditCardIcon, description: 'Precios, límites y funciones de cada plan.' },
      { to: 'promociones', label: 'Promociones', icon: BadgePercentIcon, description: 'Descuentos para todo el marketplace.' },
      { to: 'catalogo', label: 'Catálogo', icon: TagsIcon, description: 'Categorías y fabricantes oficiales.' },
    ],
  },
  {
    label: 'Inteligencia',
    items: [
      { to: 'estadisticas', label: 'Estadísticas', icon: BarChart3Icon, description: 'Tráfico, cuentas, accesos y comercio.' },
      { to: 'clientes', label: 'Clientes', icon: UsersIcon, description: 'Segmentos de compradores con consentimiento.' },
      { to: 'busquedas', label: 'Búsquedas', icon: SearchIcon, description: 'Demanda y búsquedas sin resultado.' },
    ],
  },
]
