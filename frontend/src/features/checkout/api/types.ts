/**
 * Contrato del checkout del MVP (`docs/architecture/2026-10-14-mvp-checkout-plan.md` §2).
 * El frontend nunca envía ni calcula montos que se cobren: el servidor recalcula todo y
 * rechaza campos desconocidos (`INVALID_REQUEST_BODY`).
 */

/**
 * Código de método de pago. Lo decide el servidor (`GET /marketplace/checkout-options`):
 * `WOMPI` con el proveedor real, `SIMULATED_*` solo en desarrollo.
 */
export type PaymentMethodCode = string

export type PaymentProviderCode = 'SIMULATED' | 'WOMPI'

/** GET /api/v1/marketplace/checkout-options (`docs/api/payments-wompi.md` §1). */
export interface CheckoutOptionsDto {
  provider: PaymentProviderCode
  /** `SANDBOX` | `PRODUCTION` con WOMPI; `null` con SIMULATED. */
  environment: string | null
  methods: Array<{ code: PaymentMethodCode; name: string }>
}

export type SimulatedOutcome = 'APPROVED' | 'REJECTED'

/** POST /api/v1/marketplace/orders — cuerpo. No incluir precios. */
export interface PlaceOrderRequestDto {
  clientRequestId: string
  addressId: string
  paymentMethod: PaymentMethodCode
  /** Solo con el proveedor SIMULATED; con WOMPI se envía `null`. */
  simulatedOutcome?: SimulatedOutcome | null
  items: Array<{ productId: string; branchId: string; quantity: number }>
}

/** Respuesta 202 (o 200 si es repetición) con el proveedor WOMPI (`payments-wompi.md` §2.2). */
export interface WompiCheckoutDto {
  orderId: string
  orderNumber: number
  /** `AWAITING_PAYMENT` | `CONFIRMED` | `CANCELLED` | … */
  status: string
  payment: {
    reference: string
    amountInCents: number
    currency: string
    /** Enlace firmado del checkout de Wompi; `null` si el pedido ya no se puede pagar. No se modifica. */
    checkoutUrl: string | null
    expiresAt: string | null
  }
}

/** Lo que devuelve `placeOrder`: el recibo (SIMULATED) o el enlace de pago (WOMPI). */
export type PlaceOrderResult = { kind: 'receipt'; order: BuyerOrderDto } | { kind: 'redirect'; checkout: WompiCheckoutDto }

/** Estado de Autopartco del pago de un pedido: el único que decide la interfaz. */
export type OrderPaymentStatusCode = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'

/** GET /api/v1/me/orders/{orderId}/payment y POST …/payment/sync (§3 y §4). */
export interface OrderPaymentDto {
  orderId: string
  reference: string
  status: OrderPaymentStatusCode | (string & {})
  /** Estado crudo de Wompi; solo para soporte. */
  providerStatus: string | null
  orderStatus: string
  amountInCents: number
  currency: string
  /** Solo existe mientras el pedido se puede pagar. */
  checkoutUrl: string | null
  expiresAt: string | null
  paidAt: string | null
}

/** Respuesta 201/200 con el proveedor SIMULATED (se tratan igual). */
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
  /** Proveedor activo y métodos de pago. */
  options(): Promise<CheckoutOptionsDto>
  /** Lanza `ApiError` (códigos en `docs/api/payments-wompi.md` §2.3). */
  placeOrder(input: PlaceOrderRequestDto): Promise<PlaceOrderResult>
  /** Lanza `ApiError` 404 `ORDER_NOT_FOUND` si no existe o es ajeno. */
  paymentStatus(orderId: string): Promise<OrderPaymentDto>
  /** Pide al servidor consultar la transacción en Wompi (§4). 429 `PAYMENT_SYNC_RATE_LIMITED` si se abusa. */
  syncPayment(orderId: string, transactionId: string): Promise<OrderPaymentDto>
}

export interface AddressService {
  readonly simulated: boolean
  list(): Promise<PersonAddressDto[]>
  create(input: PersonAddressRequestDto): Promise<PersonAddressDto>
}
