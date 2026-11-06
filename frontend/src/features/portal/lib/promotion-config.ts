import type { PromotionService } from '../api/promotion-service'
import type { Promotion, PromotionAudience, PromotionTargetType } from '../api/promotion-types'

export interface TargetOption {
  id: string
  label: string
}

export interface TargetOptions {
  products: TargetOption[]
  categories: TargetOption[]
  manufacturers: TargetOption[]
}

/**
 * Lo que distingue a las promociones del almacén de las de plataforma. El portal y la administración usan la
 * misma lista, formulario y resultados: cambian el servicio, las audiencias y los destinos permitidos.
 */
export interface PromotionConfig {
  scope: 'STORE' | 'PLATFORM'
  /** Almacén del portal; vacío en administración. */
  storeId: string
  service: PromotionService
  /** Prefijo de caché (se descarta al invalidar). */
  cacheKey: string
  audiences: PromotionAudience[]
  /** `false` si el plan no incluye Reportes avanzados: «Clientes frecuentes de Autopartco» queda deshabilitada. */
  platformAudienceAllowed: boolean
  targetTypes: PromotionTargetType[]
  canCreate: boolean
  canUpdate: boolean
  canDelete: boolean
  loadTargetOptions: () => Promise<TargetOptions>
  /** Si el error es de plan, abre el diálogo de mejora y devuelve `true`. */
  promptUpgrade?: (error: unknown) => boolean
  /** Tras crear, editar o eliminar. */
  invalidate: () => void
  /** Enlace a los planes (solo portal), para explicar la audiencia deshabilitada. */
  plansPath?: string
}

export type { Promotion }
