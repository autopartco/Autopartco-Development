import { invalidateAsync } from '@/lib/use-async'
import { getMarketplaceService } from '@/features/marketplace/api/marketplace-service'
import { PromotionsManager } from '@/features/portal/components/promotions-manager'
import type { PromotionConfig } from '@/features/portal/lib/promotion-config'
import { PLATFORM_AUDIENCES } from '@/features/portal/lib/promotions'
import { lazyAdminPromotionService } from '../api/admin-promotion-service'
import { AuroraHeader } from '../components/aurora-header'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const CACHE_KEY = `${ADMIN_CACHE_PREFIX}promotions`

/** Promociones de plataforma: aplican a los productos de todos los almacenes. Solo ROOT. */
const CONFIG: PromotionConfig = {
  scope: 'PLATFORM',
  storeId: '',
  service: lazyAdminPromotionService,
  cacheKey: CACHE_KEY,
  audiences: PLATFORM_AUDIENCES,
  platformAudienceAllowed: true,
  targetTypes: ['ALL', 'CATEGORY', 'MANUFACTURER', 'ATTRIBUTE'],
  canCreate: true,
  canUpdate: true,
  canDelete: true,
  loadTargetOptions: async () => {
    const options = await (await getMarketplaceService()).getFilterOptions()
    return {
      products: [],
      categories: options.categories.map((category) => ({ id: category.id, label: category.name })),
      manufacturers: options.brands.map((brand) => ({ id: brand.id, label: brand.name })),
    }
  },
  invalidate: () => invalidateAsync(CACHE_KEY),
}

/** `/admin/promociones`: promociones de plataforma con audiencia (todos o clientes frecuentes) y resultados. */
export default function AdminPromotionsPage() {
  return (
    <main>
      <title>Promociones | Administración | Autopartco</title>
      <AuroraHeader
        label="Plataforma"
        title="Promociones"
        description="Descuentos de Autopartco para los productos de todos los almacenes. Elige si los ve cualquier comprador o solo los clientes frecuentes, y mide su efecto."
      />
      <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <PromotionsManager config={CONFIG} />
      </div>
    </main>
  )
}
