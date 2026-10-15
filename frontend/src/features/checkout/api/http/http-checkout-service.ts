import { apiRequest, ApiError } from '@/lib/api'
import { getAuthService } from '@/features/auth/api/auth-service'
import type { BuyerOrderDto, CheckoutService, PlaceOrderRequestDto } from '../types'

/** Token de la sesión, o 401 sin llamar al servidor (el checkout exige sesión). */
export async function requireAccessToken(): Promise<string> {
  const token = await (await getAuthService()).getAccessToken()
  if (!token) throw new ApiError('Tu sesión venció. Ingresa de nuevo para continuar.', { status: 401, code: 'UNAUTHORIZED' })
  return token
}

/**
 * Checkout contra el backend (`marketplace/checkout/MarketplaceOrderController`,
 * `POST /marketplace/orders`). El cuerpo lleva solo ids y cantidades: el precio, el
 * stock y el estado del pago los decide el servidor (un campo de precio daría 400).
 * `200` (repetición idempotente) y `201` se tratan igual.
 */
export function createHttpCheckoutService(): CheckoutService {
  return {
    simulated: false,
    async placeOrder(input: PlaceOrderRequestDto) {
      const token = await requireAccessToken()
      return apiRequest<BuyerOrderDto>('/marketplace/orders', {
        method: 'POST',
        body: {
          clientRequestId: input.clientRequestId,
          addressId: input.addressId,
          paymentMethod: input.paymentMethod,
          simulatedOutcome: input.simulatedOutcome,
          items: input.items.map(({ productId, branchId, quantity }) => ({ productId, branchId, quantity })),
        },
        token,
      })
    },
  }
}
