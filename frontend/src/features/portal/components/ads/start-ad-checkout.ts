import { isSafeCheckoutUrl } from '@/features/checkout/lib/order-payment'
import { adsApi, type AdCampaign } from '../../api/ads-api'
import { savePendingAdCheckout } from '../../lib/ads'

/**
 * Pide el cobro al servidor (el importe lo calcula él), guarda lo necesario para consultar el estado al
 * volver y redirige a Wompi. Lanza si el servidor rechaza la petición o la URL no es https.
 */
export async function startAdCheckout(storeId: string, campaign: Pick<AdCampaign, 'id' | 'name'>): Promise<void> {
  const checkout = await adsApi.checkout(storeId, campaign.id)
  if (!isSafeCheckoutUrl(checkout.checkoutUrl)) throw new Error('La dirección de pago no es válida. Inténtalo de nuevo o escríbenos.')
  savePendingAdCheckout({
    storeId,
    campaignId: campaign.id,
    campaignName: campaign.name,
    reference: checkout.reference,
    amountInCents: checkout.amountInCents,
    startedAt: new Date().toISOString(),
  })
  window.location.assign(checkout.checkoutUrl)
}
