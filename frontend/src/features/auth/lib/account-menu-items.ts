import {
  ClipboardListIcon,
  CreditCardIcon,
  FileClockIcon,
  LayoutDashboardIcon,
  PackageIcon,
  ShoppingBagIcon,
  ShieldCheckIcon,
  ShoppingCartIcon,
  StoreIcon,
  type LucideIcon,
} from 'lucide-react'

/**
 * Única definición de las opciones del menú de cuenta. La consumen `HeaderAccount` y
 * `MobileMenuAccount` (marketplace) y `PortalAccountMenu` (portal de almacenes), para que
 * los dos menús ofrezcan lo mismo según el tipo de cuenta.
 *
 * El tipo de cuenta sale del claim `roles` del token y SOLO decide qué se muestra:
 * cada pantalla y cada petición las autoriza el servidor. Solo se enlazan rutas que
 * existen en `app/router.tsx` (hoy no hay "Mi perfil" ni "Mis pedidos" del cliente).
 */
export type AccountKind = 'store' | 'customer' | 'admin' | 'unknown'

/** Dónde se abre el menú: cambia el enlace de "ir al otro lado" (portal o marketplace). */
export type AccountMenuArea = 'marketplace' | 'portal'

export interface AccountMenuLink {
  key: string
  label: string
  to: string
  icon: LucideIcon
  /** Permiso de tienda que hace visible la opción dentro del portal (el servidor sigue decidiendo). */
  permission?: string
}

export const ACCOUNT_KIND_LABEL: Record<AccountKind, string> = {
  store: 'Almacén',
  customer: 'Cliente',
  admin: 'Administrador',
  unknown: 'Cuenta',
}

export function accountKindOf(roles: readonly string[]): AccountKind {
  if (roles.includes('ROLE_STORE_MANAGER')) return 'store'
  if (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_ROOT')) return 'admin'
  if (roles.includes('ROLE_CUSTOMER')) return 'customer'
  return 'unknown'
}

interface MenuOptions {
  kind: AccountKind
  area: AccountMenuArea
  /** Almacén abierto (o el último visitado) para enlazar sus secciones; sin él, solo "Mis almacenes". */
  storeId?: string
  /** Filtro de permisos del portal; fuera del portal no se filtra. */
  can?: (permission: string) => boolean
  /** Ruta de la solicitud de almacén en curso del cliente; sin ella no se muestra la opción. */
  sellerApplicationTo?: string
  /** Cuenta ROOT: ofrece el servicio de administración (`/admin`). El servidor sigue decidiendo. */
  root?: boolean
}

const CART: AccountMenuLink = { key: 'carrito', label: 'Carrito', to: '/carrito', icon: ShoppingCartIcon }
const MARKETPLACE: AccountMenuLink = { key: 'marketplace', label: 'Ir al marketplace', to: '/', icon: ShoppingBagIcon }

function storeSections(storeId: string): AccountMenuLink[] {
  const base = `/portal/${encodeURIComponent(storeId)}`
  return [
    { key: 'inicio', label: 'Panel', to: `${base}/inicio`, icon: LayoutDashboardIcon, permission: 'STORE_READ' },
    { key: 'pedidos', label: 'Pedidos', to: `${base}/pedidos`, icon: ClipboardListIcon, permission: 'ORDER_READ' },
    { key: 'productos', label: 'Productos', to: `${base}/productos`, icon: PackageIcon, permission: 'PRODUCT_READ' },
    { key: 'plan', label: 'Plan y suscripción', to: `${base}/plan`, icon: CreditCardIcon, permission: 'STORE_READ' },
  ]
}

/**
 * Grupos de enlaces (se separan con una línea). "Cerrar sesión" no va aquí: cada menú
 * lo añade al final porque su comportamiento depende del área.
 */
export function accountMenuGroups({ kind, area, storeId, can, sellerApplicationTo, root }: MenuOptions): AccountMenuLink[][] {
  const groups: AccountMenuLink[][] = []

  if (kind === 'store') {
    const sections = storeId ? storeSections(storeId).filter((item) => !can || !item.permission || can(item.permission)) : []
    groups.push([
      { key: 'almacenes', label: area === 'portal' ? 'Mis almacenes' : 'Ir al portal del almacén', to: '/portal', icon: StoreIcon },
      ...sections,
    ])
    groups.push(area === 'portal' ? [MARKETPLACE, CART] : [CART])
  } else if (kind === 'customer') {
    if (sellerApplicationTo) {
      groups.push([
        { key: 'solicitud-almacen', label: 'Ver mi solicitud de almacén', to: sellerApplicationTo, icon: FileClockIcon },
      ])
    }
    groups.push(area === 'portal' ? [MARKETPLACE, CART] : [CART])
  } else {
    if (root) groups.push([{ key: 'administracion', label: 'Administración', to: '/admin', icon: ShieldCheckIcon }])
    // Admin u otro rol sin menú propio: solo la salida del portal.
    if (area === 'portal') groups.push([MARKETPLACE])
  }

  return groups.filter((group) => group.length > 0)
}

// ------------------------------------------------- último almacén abierto

const LAST_STORE_KEY = 'autopartco:portal-ultimo-almacen'

/**
 * Recuerda el último almacén abierto en el portal para enlazar sus secciones desde el
 * marketplace. Es solo un atajo de navegación (el id no es secreto y el servidor
 * vuelve a comprobar el acceso); se borra al cerrar sesión.
 */
export function rememberPortalStore(storeId: string): void {
  try {
    window.sessionStorage.setItem(LAST_STORE_KEY, storeId)
  } catch {
    // Sin almacenamiento: el menú solo ofrece "Ir al portal".
  }
}

export function lastPortalStore(): string | undefined {
  try {
    return window.sessionStorage.getItem(LAST_STORE_KEY) ?? undefined
  } catch {
    return undefined
  }
}

export function forgetPortalStore(): void {
  try {
    window.sessionStorage.removeItem(LAST_STORE_KEY)
  } catch {
    // Nada que limpiar.
  }
}
