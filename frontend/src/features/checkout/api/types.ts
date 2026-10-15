/**
 * Contrato del checkout del MVP (`docs/architecture/2026-10-14-mvp-checkout-plan.md` §2).
 * El frontend nunca envía ni calcula montos que se cobren: el servidor recalcula todo y
 * rechaza campos desconocidos (`INVALID_REQUEST_BODY`).
 */

export type PaymentMethodCode = 'SIMULATED_CARD' | 'SIMULATED_TRANSFER' | 'SIMULATED_CASH'

/** Semilla `payment_methods` de init.sql. No hay endpoint de métodos en el MVP. */
export const PAYMENT_METHODS: ReadonlyArray<{ code: PaymentMethodCode; name: string; hint: string }> = [
  { code: 'SIMULATED_CARD', name: 'Tarjeta simulada', hint: 'Pago inmediato con una tarjeta de demostración.' },
  { code: 'SIMULATED_TRANSFER', name: 'Transferencia simulada', hint: 'Transferencia bancaria de demostración.' },
  { code: 'SIMULATED_CASH', name: 'Efectivo simulado', hint: 'Pago en efectivo de demostración.' },
]

export type SimulatedOutcome = 'APPROVED' | 'REJECTED'

/** POST /api/v1/marketplace/orders — cuerpo. No incluir precios. */
export interface PlaceOrderRequestDto {
  clientRequestId: string
  addressId: string
  paymentMethod: PaymentMethodCode
  simulatedOutcome?: SimulatedOutcome
  items: Array<{ productId: string; branchId: string; quantity: number }>
}

/** Respuesta 201/200 (se tratan igual). */
export interface BuyerOrderDto {
  orderId: string
  orderNumber: number
  status: string
  /** `LocalDateTime` sin zona, hora del servidor. */
  createdAt: string
  currency: 'COP'
  subtotal: number
  shippingTotal: number
  total: number
  payment: {
    status: string
    method: PaymentMethodCode
    methodName: string
    reference: string
    amount: number
    paidAt: string | null
  }
  deliveryAddress: {
    addressLine: string
    addressComplement: string | null
    city: string
    department: string
    country: string
    postalCode: string | null
  }
  sales: BuyerSaleDto[]
}

export interface BuyerSaleDto {
  saleId: string
  storeId: string
  storeName: string
  branchId: string
  branchName: string
  status: string
  subtotal: number
  shippingCost: number
  total: number
  items: BuyerOrderItemDto[]
}

export interface BuyerOrderItemDto {
  productId: string
  name: string
  partNumber: string | null
  quantity: number
  unitPrice: number
  lineTotal: number
}

/** `details.items` de los errores con líneas. */
export interface CheckoutLineRef {
  productId: string
  branchId: string
}
export interface StockShortage extends CheckoutLineRef {
  requested: number
  available: number
}

/** `details` del 402 `PAYMENT_REJECTED`. */
export interface PaymentRejectedDetails {
  paymentMethod: PaymentMethodCode
  amount: number
  currency: string
}

/** GET /api/v1/me/addresses -> PersonAddressDto[] (sin envoltorio). */
export interface PersonAddressDto {
  id: string
  label: string
  isDefault: boolean
  addressLine: string
  addressComplement: string | null
  city: string
  department: string
  country: string
  postalCode: string | null
  latitude: number | null
  longitude: number | null
}

/** POST /api/v1/me/addresses -> 201 PersonAddressDto. */
export interface PersonAddressRequestDto {
  label: string
  isDefault: boolean
  location: {
    addressLine: string
    addressComplement?: string
    city: string
    department: string
    country?: string
    postalCode?: string
    latitude?: number
    longitude?: number
  }
}

/** Formato de `localStorage['autopartco.cart.v1']` (lo escribe F1/F2; lo lee el checkout). */
export interface StoredCart {
  version: 1
  updatedAt: string
  lines: CartLine[]
}

/** Una línea por par productId + branchId. */
export interface CartLine {
  productId: string
  /** En datos mock es igual a `storeId` (los mocks no tienen sucursales). */
  branchId: string
  /** Entero de 1 a 99. */
  quantity: number
  // Instantánea solo para mostrar; el servidor recalcula todo.
  name: string
  storeId: string
  storeName?: string
  branchName?: string
  /** Precio efectivo visto al agregar (informativo). */
  unitPrice: number
  imageUrl?: string
  addedAt: string
}

export interface CheckoutService {
  /** `true` con la fuente `mock`: no se cobra ni se crea ningún pedido real. */
  readonly simulated: boolean
  /** Lanza `ApiError` (ver §2.4 del plan para los códigos). */
  placeOrder(input: PlaceOrderRequestDto): Promise<BuyerOrderDto>
}

export interface AddressService {
  readonly simulated: boolean
  list(): Promise<PersonAddressDto[]>
  create(input: PersonAddressRequestDto): Promise<PersonAddressDto>
}
