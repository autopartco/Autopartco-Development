import { BellIcon, KeyRoundIcon, LayoutGridIcon, MapPinIcon, PackageIcon, ShieldIcon, SlidersHorizontalIcon, StarIcon, UserRoundIcon, type LucideIcon } from 'lucide-react'

export interface AccountLink {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
}

/** Secciones de «Mi cuenta»: las mismas para clientes, almacenes y administradores. */
export const ACCOUNT_LINKS: AccountLink[] = [
  { to: '/cuenta', label: 'Resumen', icon: LayoutGridIcon, end: true },
  { to: '/cuenta/perfil', label: 'Datos personales', icon: UserRoundIcon },
  { to: '/cuenta/seguridad', label: 'Seguridad', icon: KeyRoundIcon },
  { to: '/cuenta/direcciones', label: 'Direcciones', icon: MapPinIcon },
  { to: '/cuenta/preferencias', label: 'Preferencias', icon: SlidersHorizontalIcon },
  { to: '/cuenta/privacidad', label: 'Privacidad y datos', icon: ShieldIcon },
]

/** Accesos de compra que también viven en la cuenta (páginas propias). */
export const ACCOUNT_SHORTCUTS: AccountLink[] = [
  { to: '/mis-pedidos', label: 'Mis pedidos', icon: PackageIcon },
  { to: '/cuenta/calificar', label: 'Por calificar', icon: StarIcon },
  { to: '/cuenta/notificaciones', label: 'Notificaciones', icon: BellIcon },
]

