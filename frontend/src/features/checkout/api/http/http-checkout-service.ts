import { apiRequest, ApiError } from '@/lib/api'
import { getAuthService } from '@/features/auth/api/auth-service'
import type { BuyerOrderDto, CheckoutOptionsDto, CheckoutService, OrderPaymentDto, PlaceOrderRequestDto, WompiCheckoutDto } from '../types'

/** Token de la sesión, o 401 sin llamar al servidor (el checkout exige sesión). */
export async function requireAccessToken(): Promise<string> {
  const token = await (await getAuthService()).getAccessToken()
  if (!token) throw new ApiError('Tu sesión venció. Ingresa de nuevo para continuar.', { status: 401, code: 'UNAUTHORIZED' })
  return token
}

/**
 * La respuesta de WOMPI (`202`/`200`) trae `payment.checkoutUrl` y no trae `sales`; la de SIMULATED
 * (`201`/`200`) es el recibo completo. Se distinguen por la forma porque `apiRequest` no expone el código.
 */
function isWompiCheckout(value: BuyerOrderDto | WompiCheckoutDto): value is WompiCheckoutDto {
  return !Array.isArray((value as Partial<BuyerOrderDto>).sales) && typeof value.payment === 'object' && value.payment !== null && 'checkoutUrl' in value.payment
}

const paymentPath = (orderId: string) => `/me/orders/${encodeURIComponent(orderId)}/payment`

/**
 * Checkout contra el backend (`marketplace/checkout/MarketplaceOrderController` y
 * `OrderPaymentController`). El cuerpo lleva solo ids y cantidades: el precio, el stock y el
 * estado del pago los decide el servidor.
 */
export function createHttpCheckoutService(): CheckoutService {
  return {
    simulated: false,
    async options() {
      const token = await requireAccessToken()
      return apiRequest<CheckoutOptionsDto>('/marketplace/checkout-options', { token })
    },
    async placeOrder(input: PlaceOrderRequestDto) {
      const token = await requireAccessToken()
      const body = await apiRequest<BuyerOrderDto | WompiCheckoutDto>('/marketplace/orders', {
        method: 'POST',
        body: {
          clientRequestId: input.clientRequestId,
          addressId: input.addressId,
          paymentMethod: input.paymentMethod,
          simulatedOutcome: input.simulatedOutcome ?? null,
          items: input.items.map(({ productId, branchId, quantity }) => ({ productId, branchId, quantity })),
        },
        token,
      })
      return isWompiCheckout(body) ? { kind: 'redirect', checkout: body } : { kind: 'receipt', order: body }
    },
    async paymentStatus(orderId) {
      const token = await requireAccessToken()
      return apiRequest<OrderPaymentDto>(paymentPath(orderId), { token })
    },
    async syncPayment(orderId, transactionId) {
      const token = await requireAccessToken()
      return apiRequest<OrderPaymentDto>(`${paymentPath(orderId)}/sync`, { method: 'POST', body: { transactionId }, token })
    },
  }
}
