import { useSession } from '@/features/auth/hooks'
import { accountKindOf } from '@/features/auth/lib/account-menu-items'
import { useAsync } from '@/lib/use-async'
import { getSellerApplicationService } from './api/seller-service'
import type { MyStoreApplication } from './api/types'

/**
 * Vista del portal donde la cuenta ve el estado de su solicitud de almacén (fecha, estado,
 * comentarios de la revisión y siguiente paso).
 */
export const SELLER_APPLICATION_PATH = '/portal/solicitud'

/** Configuración del almacén aprobado (con `?token=` del correo o con la sesión iniciada). */
export const SELLER_SETUP_PATH = '/seller/setup'

/** Prefijo de caché: se invalida al cerrar sesión y al enviar una solicitud. */
export const SELLER_CACHE_PREFIX = 'seller:'

/**
 * Relación de la cuenta con el registro de almacén. Solo decide qué se muestra; el servidor
 * valida cada solicitud.
 * - `register`: puede registrar (sin sesión, cliente sin solicitud o con una rechazada).
 * - `review`: solicitud en curso (`PENDING_REVIEW` o `CONFIGURATION_PENDING`), aún sin almacén operativo.
 * - `manage`: ya administra un almacén (rol de almacén o solicitud `ACTIVE`).
 * - `learn`: cuenta que no puede solicitar (administrador u otra): solo información.
 * - `loading`: consultando las solicitudes del cliente.
 */
export type SellerState = 'register' | 'review' | 'manage' | 'learn' | 'loading'

export interface SellerCta {
  state: SellerState
  to: string
  label: string
  /** Solicitud en curso, si la hay. */
  application?: MyStoreApplication
}

const IN_REVIEW: readonly MyStoreApplication['status'][] = ['PENDING_REVIEW', 'CONFIGURATION_PENDING']

export function useSellerCta(): SellerCta {
  const session = useSession()
  const kind = session ? accountKindOf(session.roles) : undefined
  const isCustomer = kind === 'customer'
  // La clave lleva el correo para no mezclar cuentas; sin cliente con sesión no hay petición.
  const applications = useAsync<MyStoreApplication[]>(
    `${SELLER_CACHE_PREFIX}applications:${isCustomer ? session?.user.email : 'none'}`,
    async () => (isCustomer ? (await getSellerApplicationService()).myApplications() : []),
  )

  if (kind === 'store') return { state: 'manage', to: '/portal', label: 'Ir a mi almacén' }
  if (kind === 'admin' || kind === 'unknown') return { state: 'learn', to: '/vender', label: 'Vende en Autopartco' }

  if (isCustomer) {
    if (applications.status === 'loading' || applications.isStale) {
      return { state: 'loading', to: '/vender', label: 'Vende en Autopartco' }
    }
    const list = applications.data ?? []
    const active = list.find((application) => application.status === 'ACTIVE')
    if (active) return { state: 'manage', to: '/portal', label: 'Ir a mi almacén', application: active }
    const inReview = [...list]
      .filter((application) => IN_REVIEW.includes(application.status))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
    if (inReview) {
      return { state: 'review', to: SELLER_APPLICATION_PATH, label: 'Ver mi solicitud de almacén', application: inReview }
    }
  }
  return { state: 'register', to: '/vender/registro', label: 'Registra tu almacén' }
}
