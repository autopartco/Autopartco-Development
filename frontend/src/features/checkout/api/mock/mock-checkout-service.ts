import { ApiError, simulateLatency } from '@/lib/api'
import { MOCK_PRODUCTS } from '@/features/marketplace/api/mock/mock-data'
import type { BuyerOrderDto, BuyerSaleDto, CheckoutService, PlaceOrderRequestDto } from '../types'
import { PAYMENT_METHODS } from '../types'
import { findMockAddress } from './mock-address-service'

/** Existencias que "tiene" cualquier listado simulado. */
const MOCK_AVAILABLE_STOCK = 5
const FALLBACK_PRICE = 50000

/**
 * Checkout simulado, determinista. Reglas, en este orden de prioridad:
 * 1. `simulatedOutcome === 'REJECTED'` -> 402 `PAYMENT_REJECTED` con `details { paymentMethod, amount, currency }`.
 * 2. Líneas con `quantity > 5` -> 409 `INSUFFICIENT_STOCK` con `details.items` de TODAS esas líneas (`available: 5`).
 * 3. Líneas con `productId` que empieza por `unavailable-` -> 404 `PRODUCT_NOT_FOUND` con `details.items`.
 * 4. `addressId` que no está en el mock de direcciones -> 404 `ADDRESS_NOT_FOUND`.
 * 5. Si no, un pedido `CONFIRMED` (`orderNumber` desde 1001, una venta `PENDING` por `branchId`, pago `APPROVED`).
 *    El precio sale de una tabla interna por `productId` (catálogo mock; 50.000 si no está).
 * Repetir el mismo `clientRequestId` devuelve el mismo pedido (idempotencia). Nunca se lee un precio del request.
 */
export function createMockCheckoutService(): CheckoutService {
  const byRequest = new Map<string, BuyerOrderDto>()
  let nextNumber = 1001

  function priceOf(productId: string): number {
    return MOCK_PRODUCTS.find((product) => product.id === productId)?.price ?? FALLBACK_PRICE
  }

  function linesWhere(input: PlaceOrderRequestDto, predicate: (line: PlaceOrderRequestDto['items'][number]) => boolean) {
    return input.items.filter(predicate)
  }

  function build(input: PlaceOrderRequestDto): BuyerOrderDto {
    const address = findMockAddress(input.addressId)
    const sales = new Map<string, BuyerSaleDto>()
    for (const line of input.items) {
      const product = MOCK_PRODUCTS.find((candidate) => candidate.id === line.productId)
      const unitPrice = priceOf(line.productId)
      let sale = sales.get(line.branchId)
      if (!sale) {
        sale = {
          saleId: crypto.randomUUID(),
          storeId: product?.store?.id ?? line.branchId,
          storeName: product?.store?.name ?? 'Tienda de repuestos',
          branchId: line.branchId,
          branchName: product?.availability?.branchName ?? 'Sede principal',
          status: 'PENDING',
          subtotal: 0,
          shippingCost: 0,
          total: 0,
          items: [],
        }
        sales.set(line.branchId, sale)
      }
      const lineTotal = unitPrice * line.quantity
      sale.items.push({
        productId: line.productId,
        name: product?.name ?? 'Repuesto',
        partNumber: product?.partNumber ?? null,
        quantity: line.quantity,
        unitPrice,
        lineTotal,
      })
      sale.subtotal += lineTotal
      sale.total += lineTotal
    }
    const ordered = [...sales.values()].sort((a, b) => a.storeName.localeCompare(b.storeName, 'es') || a.branchName.localeCompare(b.branchName, 'es'))
    for (const sale of ordered) sale.items.sort((a, b) => a.name.localeCompare(b.name, 'es'))
    const subtotal = ordered.reduce((sum, sale) => sum + sale.subtotal, 0)
    const now = new Date().toISOString().slice(0, 19)
    return {
      orderId: crypto.randomUUID(),
      orderNumber: nextNumber++,
      status: 'CONFIRMED',
      createdAt: now,
      currency: 'COP',
      subtotal,
      shippingTotal: 0,
      total: subtotal,
      payment: {
        status: 'APPROVED',
        method: input.paymentMethod,
        methodName: PAYMENT_METHODS.find((method) => method.code === input.paymentMethod)?.name ?? input.paymentMethod,
        reference: input.clientRequestId,
        amount: subtotal,
        paidAt: now,
      },
      deliveryAddress: {
        addressLine: address?.addressLine ?? '',
        addressComplement: address?.addressComplement ?? null,
        city: address?.city ?? '',
        department: address?.department ?? '',
        country: address?.country ?? 'Colombia',
        postalCode: address?.postalCode ?? null,
      },
      sales: ordered,
    }
  }

  return {
    simulated: true,

    async placeOrder(input) {
      await simulateLatency(900)
      const replay = byRequest.get(input.clientRequestId)
      if (replay) return replay

      if (input.simulatedOutcome === 'REJECTED') {
        const amount = input.items.reduce((sum, line) => sum + priceOf(line.productId) * line.quantity, 0)
        throw new ApiError(
          'El pago simulado fue rechazado. No se hizo ningún cobro ni se creó el pedido; puedes intentarlo con otro método.',
          { status: 402, code: 'PAYMENT_REJECTED', details: { paymentMethod: input.paymentMethod, amount, currency: 'COP' } },
        )
      }
      const short = linesWhere(input, (line) => line.quantity > MOCK_AVAILABLE_STOCK)
      if (short.length > 0) {
        throw new ApiError('No hay unidades suficientes de uno o más repuestos. Ajusta las cantidades e inténtalo de nuevo.', {
          status: 409,
          code: 'INSUFFICIENT_STOCK',
          details: {
            items: short.map(({ productId, branchId, quantity }) => ({ productId, branchId, requested: quantity, available: MOCK_AVAILABLE_STOCK })),
          },
        })
      }
      const gone = linesWhere(input, (line) => line.productId.startsWith('unavailable-'))
      if (gone.length > 0) {
        throw new ApiError('Uno o más repuestos ya no están disponibles. Quítalos del carrito para continuar.', {
          status: 404,
          code: 'PRODUCT_NOT_FOUND',
          details: { items: gone.map(({ productId, branchId }) => ({ productId, branchId })) },
        })
      }
      if (!findMockAddress(input.addressId)) {
        throw new ApiError('No se encontró la dirección.', { status: 404, code: 'ADDRESS_NOT_FOUND' })
      }

      const order = build(input)
      byRequest.set(input.clientRequestId, order)
      return order
    },
  }
}
