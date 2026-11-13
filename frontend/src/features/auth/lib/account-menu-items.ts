import {
  BellIcon,
  ClipboardListIcon,
  StarIcon,
  UserRoundIcon,
  CreditCardIcon,
  FileClockIcon,
  GiftIcon,
  LayoutDashboardIcon,
  PackageIcon,
  ShoppingBagIcon,
  SettingsIcon,
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
 * existen en `app/router.tsx`. Todas las cuentas comparten el grupo «Mi cuenta» (perfil, pedidos, por calificar,
 * notificaciones); después van las opciones propias del rol.
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
  /** Permisos de la cuenta en `storeId`; sin ellos no se enlazan secciones del almacén. */
  can?: (permission: string) => boolean
  /** Ruta de la solicitud de almacén en curso del cliente; sin ella no se muestra la opción. */
  sellerApplicationTo?: string
  /** Ruta para terminar la configuración de una solicitud aprobada (`CONFIGURATION_PENDING`); sin ella no se muestra. */
  sellerSetupTo?: string
  /** Cuenta ROOT: ofrece el servicio de administración (`/admin`). El servidor sigue decidiendo. */
  root?: boolean
  /** Cliente con almacén activo (solicitud propia o colaborador invitado): ofrece el portal. */
  hasStore?: boolean
}

const MY_ACCOUNT: AccountMenuLink = { key: 'mi-cuenta', label: 'Mi cuenta', to: '/cuenta', icon: UserRoundIcon }
const MY_ORDERS: AccountMenuLink = { key: 'mis-pedidos', label: 'Mis pedidos', to: '/mis-pedidos', icon: PackageIcon }
const TO_RATE: AccountMenuLink = { key: 'por-calificar', label: 'Por calificar', to: '/cuenta/calificar', icon: StarIcon }
const NOTIFICATIONS: AccountMenuLink = { key: 'notificaciones', label: 'Notificaciones', to: '/cuenta/notificaciones', icon: BellIcon }
/** Igual para cualquier cuenta: un almacén o un administrador también compra y administra su cuenta. */
const COMMON: AccountMenuLink[] = [MY_ACCOUNT, MY_ORDERS, TO_RATE, NOTIFICATIONS]
const MY_BENEFITS: AccountMenuLink = { key: 'mis-beneficios', label: 'Tus beneficios', to: '/mis-beneficios', icon: GiftIcon }
const CART: AccountMenuLink = { key: 'carrito', label: 'Carrito', to: '/carrito', icon: ShoppingCartIcon }
const MARKETPLACE: AccountMenuLink = { key: 'marketplace', label: 'Ir al marketplace', to: '/buscar', icon: ShoppingBagIcon }

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
export function accountMenuGroups({
  kind,
  area,
  storeId,
  can,
  sellerApplicationTo,
  sellerSetupTo,
  root,
  hasStore,
}: MenuOptions): AccountMenuLink[][] {
  const groups: AccountMenuLink[][] = [COMMON]
  const storeGroup = (): AccountMenuLink[] => [
    { key: 'almacenes', label: area === 'portal' ? 'Mis almacenes' : 'Ir al portal del almacén', to: '/portal', icon: StoreIcon },
    // En el portal las secciones ya están en la barra lateral; fuera, solo con permisos conocidos.
    ...(area !== 'portal' && storeId && can ? storeSections(storeId).filter((item) => !item.permission || can(item.permission)) : []),
  ]

  if (kind === 'store') {
    groups.push(storeGroup())
    groups.push(area === 'portal' ? [MARKETPLACE, CART] : [CART])
  } else if (kind === 'customer') {
    if (hasStore) groups.push(storeGroup())
    const seller: AccountMenuLink[] = []
    if (sellerSetupTo) {
      seller.push({ key: 'configuracion-almacen', label: 'Terminar configuración', to: sellerSetupTo, icon: SettingsIcon })
    }
    if (sellerApplicationTo) {
      seller.push({ key: 'solicitud-almacen', label: 'Ver mi solicitud de almacén', to: sellerApplicationTo, icon: FileClockIcon })
    }
    if (seller.length > 0) groups.push(seller)
    groups.push(area === 'portal' ? [MARKETPLACE, MY_BENEFITS, CART] : [MY_BENEFITS, CART])
  } else {
    if (root) groups.push([{ key: 'administracion', label: 'Administración', to: '/admin', icon: ShieldCheckIcon }])
    if (area === 'portal') groups.push([MARKETPLACE])
  }

  return groups.filter((group) => group.length > 0)
}

// ------------------------------------------------- último almacén abierto

const LAST_STORE_KEY = 'autopartco:portal-ultimo-almacen'

/** Último almacén abierto en el portal y los permisos que la cuenta tiene en él. */
export interface LastPortalStore {
  storeId: string
  permissions: string[]
}

/**
 * Recuerda el último almacén abierto en el portal (y sus permisos) para enlazar desde el
 * marketplace solo las secciones que el rol permite. Es solo un atajo de navegación (el id no
 * es secreto y el servidor vuelve a comprobar el acceso); se borra al cerrar sesión.
 */
export function rememberPortalStore(storeId: string, permissions: readonly string[]): void {
  try {
    window.sessionStorage.setItem(LAST_STORE_KEY, JSON.stringify({ storeId, permissions }))
  } catch {
    // Sin almacenamiento: el menú solo ofrece "Ir al portal".
  }
}

export function lastPortalStore(): LastPortalStore | undefined {
  try {
    const raw = window.sessionStorage.getItem(LAST_STORE_KEY)
    if (!raw) return undefined
    const value: unknown = JSON.parse(raw)
    if (!value || typeof value !== 'object') return undefined
    const { storeId, permissions } = value as Partial<LastPortalStore>
    if (typeof storeId !== 'string' || !Array.isArray(permissions)) return undefined
    return { storeId, permissions: permissions.filter((item): item is string => typeof item === 'string') }
  } catch {
    // Valor antiguo (solo el id) o almacenamiento bloqueado: sin atajos.
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
