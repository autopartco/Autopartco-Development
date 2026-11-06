import { useMemo, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { Skeleton } from '@/components/ui/skeleton'
import { getProductService } from '../api/product-service'
import { lazyPromotionService } from '../api/promotion-service'
import { PageHeader } from '../components/page-header'
import { UpgradeNotice } from '../components/plan-gate'
import { PromotionsManager } from '../components/promotions-manager'
import { usePortal } from '../context'
import { humanizeCode } from '../lib/labels'
import type { PromotionConfig } from '../lib/promotion-config'
import { isPromotionAudience, STORE_AUDIENCES } from '../lib/promotions'

const PROMOTIONS_CAPABILITY = 'PROMOTIONS'
const REPORTS_CAPABILITY = 'ADVANCED_REPORTS'

/** `/portal/:storeId/promociones`: descuentos del almacén con audiencia y resultados. `?nueva=1&audiencia=` abre el formulario. */
export default function PortalPromotionsPage() {
  const { storeId, can, subscription, subscriptionError, promptUpgrade, invalidateStore } = usePortal()
  const [params, setParams] = useSearchParams()
  const audienceParam = params.get('audiencia')
  const startCreating = params.get('nueva') === '1' && can('PROMOTION_CREATE')
  const capabilities = subscription?.capabilities

  const config = useMemo<PromotionConfig>(
    () => ({
      scope: 'STORE',
      storeId,
      service: lazyPromotionService,
      cacheKey: `portal:${storeId}:promotions`,
      audiences: STORE_AUDIENCES,
      platformAudienceAllowed: capabilities?.includes(REPORTS_CAPABILITY) ?? false,
      targetTypes: ['STORE', 'PRODUCT', 'CATEGORY', 'MANUFACTURER', 'ATTRIBUTE'],
      canCreate: can('PROMOTION_CREATE'),
      canUpdate: can('PROMOTION_UPDATE'),
      canDelete: can('PROMOTION_DELETE'),
      loadTargetOptions: async () => {
        const service = await getProductService()
        const [options, rows] = await Promise.all([service.options(), service.list(storeId)])
        const products = new Map(rows.map((row) => [row.id, row.name]))
        return {
          products: [...products].map(([id, label]) => ({ id, label })).toSorted((a, b) => a.label.localeCompare(b.label, 'es-CO')),
          categories: options.categories.map((category) => ({ id: category.id, label: humanizeCode(category.code) })),
          manufacturers: options.manufacturers.map((manufacturer) => ({ id: manufacturer.id, label: manufacturer.name })),
        }
      },
      promptUpgrade,
      invalidate: invalidateStore,
      plansPath: `/portal/${encodeURIComponent(storeId)}/plan`,
    }),
    [storeId, capabilities, can, promptUpgrade, invalidateStore],
  )

  let body: ReactNode
  if (!subscription && !subscriptionError) {
    body = (
      <div role="status">
        <span className="sr-only">Cargando…</span>
        <Skeleton aria-hidden className="h-40 w-full" />
      </div>
    )
  } else if (subscription && !subscription.capabilities.includes(PROMOTIONS_CAPABILITY)) {
    body = <UpgradeNotice capability={PROMOTIONS_CAPABILITY} />
  } else {
    body = (
      <PromotionsManager
        config={config}
        startCreating={startCreating}
        initialAudience={isPromotionAudience(audienceParam) ? audienceParam : undefined}
        onStartHandled={() =>
          setParams(
            (current) => {
              const next = new URLSearchParams(current)
              next.delete('nueva')
              next.delete('audiencia')
              return next
            },
            { replace: true },
          )
        }
      />
    )
  }

  return (
    <>
      <title>Promociones | Autopartco</title>
      <PageHeader
        title="Promociones"
        description="Descuentos para tus productos. Elige quién ve el precio, cuándo corre y mide cuánto vendió."
      />
      {body}
    </>
  )
}
