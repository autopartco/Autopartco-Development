/**
 * Modelo del portal del almacén. Transcrito de los records del backend:
 * `storesuite/{store,subscription,dashboard,products,orders,branches,staff}` y
 * `core/{billing,catalog,order,organization}`; suscripción según plan §4.6
 * (`docs/architecture/2026-10-14-mvp-saas-vendedores-plan.md`). Las fechas son
 * `LocalDateTime` ISO sin zona (hora de Bogotá, ADR 0002). El cliente solo muestra
 * lo que el servidor devuelve: no decide permisos, precios, stock ni pagos.
 */

// ---------------------------------------------------------------- tiendas

export type StoreApplicationStatus = 'ACTIVE' | 'CONFIGURATION_PENDING' | 'PENDING_REVIEW' | 'REJECTED'
export type SubscriptionStatus = 'PENDING_SETUP' | 'TRIALING' | 'ACTIVE' | 'EXPIRED'
export type Capacity = 'PRODUCTS' | 'BRANCHES' | 'USERS'

/** `GET /me/stores`. */
export interface MyStore {
  storeId: string
  businessName: string
  logoUrl: string | null
  applicationStatus: StoreApplicationStatus
  isOwner: boolean
  roleNames: string[]
  subscription: { status: SubscriptionStatus; planCode: string; planName: string; validThrough: string | null } | null
}

/** `GET /stores/{id}/profile` (`StoreProfileResponse`). */
export interface StoreProfile {
  id: string
  businessName: string
  nit: string
  businessEmail: string
  state: string
  description: string | null
  logoUrl: string | null
  bannerUrl: string | null
}

/** `PATCH /stores/{id}` (`StoreUpdateRequest`): solo los campos presentes cambian. El NIT no se edita en la UI (D11). */
export interface StoreProfileUpdate {
  businessName?: string
  businessEmail?: string
  description?: string
}

/** Códigos de `StorePermissionCodes` que devuelve `GET /stores/{id}/my-permissions`. */
export type StorePermission =
  | 'STORE_READ'
  | 'STORE_UPDATE'
  | 'BRANCH_READ'
  | 'BRANCH_CREATE'
  | 'BRANCH_UPDATE'
  | 'BRANCH_DELETE'
  | 'PRODUCT_READ'
  | 'PRODUCT_CREATE'
  | 'PRODUCT_UPDATE'
  | 'PRODUCT_PUBLISH'
  | 'PRODUCT_DELETE'
  | 'PRODUCT_IMAGE_MANAGE'
  | 'PROMOTION_READ'
  | 'ORDER_READ'
  | 'ORDER_UPDATE'
  | 'SHIPMENT_UPDATE'
  | 'COLLABORATOR_READ'
  | 'COLLABORATOR_MANAGE'

// ------------------------------------------------------------ suscripción

/** `PlanResponse`. `max*: null` = ilimitado. */
export interface Plan {
  id: string
  code: string
  name: string
  description: string | null
  monthlyPriceCop: number
  currency: 'COP'
  features: string[]
  recommended: boolean
  enabled: boolean
  maxBranches: number | null
  maxProducts: number | null
  maxUsers: number | null
  capabilities: string[]
}

export interface UsageItem {
  used: number
  limit: number | null
}

/** `GET /stores/{id}/subscription` (plan §4.6). */
export interface Subscription {
  storeId: string
  status: SubscriptionStatus
  plan: Plan
  selectedPlan: Plan | null
  trialStartsAt: string | null
  trialEndsAt: string | null
  paidThrough: string | null
  validThrough: string | null
  daysRemaining: number | null
  usage: Record<'branches' | 'products' | 'users', UsageItem>
  capabilities: string[]
  paymentMode: 'SIMULATED' | 'WOMPI' | 'NONE'
  canManage: boolean
  planChangeAllowed: boolean
}

export interface SubscriptionPayment {
  id: string
  reference: string
  provider: 'SIMULATED' | 'WOMPI'
  planCode: string
  planName: string
  months: number
  amountCop: number
  currency: 'COP'
  status: string
  createdAt: string
  paidAt: string | null
  periodStartsAt: string | null
  periodEndsAt: string | null
}

/** `POST /stores/{id}/subscription/simulated-payments`. */
export interface SimulatedPaymentInput {
  planId: string
  months: number
  simulatedOutcome: 'APPROVED' | 'REJECTED'
  clientRequestId: string
}

export interface SimulatedPaymentResult {
  payment: SubscriptionPayment
  subscription: Subscription
}

/** `POST /stores/{id}/subscription/checkout` (Wompi, `CheckoutRequest` / `CheckoutResponse`). */
export interface CheckoutInput {
  planId: string
  months: number
}

export interface CheckoutResult {
  reference: string
  checkoutUrl: string
  amountInCents: number
  currency: string
  prepaidMonths: number
}

// -------------------------------------------------------------- dashboard

export interface SalesSummary {
  ordersThisMonth: number
  revenueThisMonth: number
  pendingOrders: number
  preparingOrders: number
}

/** `GET /stores/{id}/dashboard` (`DashboardResponse`). Los tres últimos campos son aditivos (plan §4.3). */
export interface Dashboard {
  storeId: string
  storeName: string
  storeStatus: string
  plan: Plan
  selectedPlan: Plan | null
  setupRequired: boolean
  subscriptionExpired: boolean
  trialEndsAt: string | null
  paidThrough: string | null
  maxBranches: number | null
  maxProducts: number | null
  maxUsers: number | null
  capabilities: string[]
  usage: { branches: number; products: number; users: number }
  sales: SalesSummary
  subscriptionStatus?: SubscriptionStatus
  daysRemaining?: number | null
  inventory?: { listings: number; outOfStock: number; lowStock: number }
}

// -------------------------------------------------------------- productos

export interface ProductImage {
  id: string
  /** Original. */
  url: string
  /** Variante de 400 px (listados pequeños). */
  thumbUrl: string
  /** Variante de 800 px (tarjetas). */
  cardUrl: string
  isMain: boolean
  displayOrder: number
  createdAt: string
}

/** Elemento del catálogo de vehículos en cascada (`GET /marketplace/vehicles/...`). */
export interface VehicleMake {
  id: string
  name: string
}
export interface VehicleModel {
  id: string
  name: string
}
export interface VehicleVersion {
  id: string
  label: string
}

/** Regla de entrada: nivel ausente = «todos»; `versionId` exige `modelId`. */
export interface VehicleRuleInput {
  makeId: string
  modelId?: string
  versionId?: string
}

/** `VehicleRuleResponse`: la regla con nombres legibles; los niveles «todos» vienen en `null`. */
export interface VehicleRule {
  makeId: string
  makeName: string
  modelId: string | null
  modelName: string | null
  versionId: string | null
  versionLabel: string | null
}

/** Detalle de `VEHICLE_RULE_INVALID` (400): `index` es la posición en `vehicleRules`. */
export interface VehicleRuleErrorDetail {
  index: number
  field: string
  code: string
  message: string
}

/** Detalle de `PRODUCT_NOT_PUBLISHABLE` (409): qué falta para publicar. */
export type PublishBlockerCode = 'NO_IMAGES' | 'NO_VEHICLES' | 'MANUFACTURER_INVALID' | 'SUBSCRIPTION_INACTIVE'
export interface PublishBlocker {
  code: PublishBlockerCode
  message: string
}

/** Códigos de error de producto que las pantallas distinguen (`ApiError.code`). */
export type ProductErrorCode =
  | 'VEHICLE_RULE_INVALID'
  | 'PRODUCT_NOT_PUBLISHABLE'
  | 'MANUFACTURER_SIMILAR'
  | 'MANUFACTURER_PENDING_LIMIT'
  | 'MANUFACTURER_NAME_UNAVAILABLE'

/** `ProductResponse`: una fila por (producto, sucursal). */
export interface ProductRow {
  id: string
  storeId: string
  branchId: string
  branchName: string
  name: string
  sku: string | null
  partNumber: string | null
  description: string
  price: number
  effectivePrice: number
  discountAmount: number
  appliedPromotionName: string | null
  quantity: number
  manufacturerId: string
  /** Visible aunque el fabricante esté pendiente de aprobación. */
  manufacturerName: string
  manufacturerApproved: boolean
  categoryId: string
  state: 'ACTIVE' | 'INACTIVE'
  attributes: Record<string, unknown>
  /** No depende del vehículo: aparece en cualquier búsqueda por vehículo y no lleva reglas. */
  universal: boolean
  vehicleRules: VehicleRule[]
  images: ProductImage[]
}

/** `ProductOptionsResponse` (`GET /marketplace/product-options`, público). */
export interface ProductOptions {
  manufacturers: { id: string; name: string }[]
  categories: { id: string; code: string }[]
}

/** `StoreManufacturerResponse`: fabricante aprobado o pendiente de revisión propio del almacén. */
export interface StoreManufacturer {
  id: string
  name: string
  approved: boolean
}

/** `ManufacturerSearchResult`: coincidencia aproximada, `similarity` entre 0 y 1. */
export interface ManufacturerMatch extends StoreManufacturer {
  similarity: number
}

/** `StoreManufacturerRequest`. */
export interface ManufacturerCreateInput {
  name: string
  /** `true` cuando el usuario confirma que no es ninguno de los parecidos. */
  confirmNew?: boolean
}

/**
 * Parte `data` de `POST /stores/{id}/products` (multipart). Exactamente uno de `manufacturerId` /
 * `newManufacturerName`. Las imágenes viajan aparte (`images`, orden = orden de galería).
 */
export interface ProductCreateInput {
  branchId: string
  name: string
  sku?: string
  partNumber?: string
  description: string
  price: number
  quantity: number
  categoryId: string
  manufacturerId?: string
  newManufacturerName?: string
  /** `true` cuando el usuario confirmó que el fabricante nuevo no es ninguno de los parecidos. */
  confirmNewManufacturer?: boolean
  /** Con `universal: true` no se envían reglas (400 `VALIDATION_ERROR` si llegan). */
  universal: boolean
  vehicleRules: VehicleRuleInput[]
  attributes?: Record<string, unknown>
  /** Posición en `images` de la imagen principal. */
  mainImageIndex?: number
  /** Crear y publicar en la misma petición. */
  publish: boolean
}

/** Parte `data` de `PATCH …/products/{productId}` (multipart): solo los campos presentes cambian. */
export interface ProductUpdateInput {
  name?: string
  sku?: string
  partNumber?: string
  description?: string
  price?: number
  quantity?: number
  categoryId?: string
  manufacturerId?: string
  newManufacturerName?: string
  confirmNewManufacturer?: boolean
  /** Pasar a `true` borra las reglas del servidor. */
  universal?: boolean
  /** Reemplaza el conjunto completo de reglas. */
  vehicleRules?: VehicleRuleInput[]
  attributes?: Record<string, unknown>
  publish?: boolean
  removeImageIds?: string[]
  /** Ids existentes y `new:<i>` (índice en `newImages`), en el orden final de la galería. */
  imageOrder?: string[]
  /** Id existente o `new:<i>`. */
  mainImage?: string
}

// ---------------------------------------------------------------- pedidos

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REJECTED'
export type ShipmentStatus = 'PREPARING' | 'DISPATCHED' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED'

/** `SaleSummaryResponse`. */
export interface OrderSummary {
  saleId: string
  orderId: string
  orderNumber: number
  branchId: string
  branchName: string
  status: OrderStatus
  subtotal: number
  shippingCost: number
  total: number
  createdAt: string
  customerName: string | null
  paymentStatus: string | null
  shipmentStatus: string | null
  itemCount: number
}

export interface OrderItem {
  inventoryId: string
  productId: string
  productName: string
  partNumber: string | null
  units: number
  unitPrice: number
}

export interface OrderShipment {
  id: string
  carrier: string
  trackingNumber: string
  status: ShipmentStatus
  shippedAt: string | null
  deliveredAt: string | null
  events: { status: string; notes: string | null; occurredAt: string }[]
}

/** `SaleDetailResponse`. */
export interface OrderDetail {
  saleId: string
  orderId: string
  orderNumber: number
  branchId: string
  branchName: string
  status: OrderStatus
  subtotal: number
  shippingCost: number
  total: number
  createdAt: string
  customerName: string | null
  paymentStatus: string | null
  paymentMethod: string | null
  shipment: OrderShipment | null
  items: OrderItem[]
  deliveryAddress: {
    addressLine: string
    addressComplement: string | null
    city: string
    department: string
    country: string | null
    postalCode: string | null
  } | null
}

/** `SaleStatusRequest`: solo estos tres destinos existen en el API. */
export type OrderStatusTarget = 'CONFIRMED' | 'PREPARING' | 'CANCELLED'

/** `ShipmentCreateRequest`. */
export interface ShipmentCreateInput {
  carrier: string
  trackingNumber: string
}

/** `ShipmentStatusRequest`. */
export interface ShipmentStatusInput {
  status: Exclude<ShipmentStatus, 'PREPARING'>
  notes?: string
}

// ------------------------------------------------------------- sucursales

export interface BranchLocation {
  id?: string
  addressLine: string
  addressComplement: string | null
  city: string
  department: string
  country: string | null
  postalCode: string | null
  latitude: number | null
  longitude: number | null
}

/** `BranchResponse`. */
export interface Branch {
  id: string
  storeId: string
  name: string
  numberPhone: string
  state: string
  location: BranchLocation | null
}

/** `BranchCreateRequest` / `BranchUpdateRequest` (en la edición, los campos son opcionales). */
export interface BranchInput {
  name: string
  numberPhone: string
  location: {
    addressLine: string
    addressComplement?: string
    city: string
    department: string
    country?: string
    postalCode?: string
  }
}

// ----------------------------------------------------------------- equipo

export interface StaffMember {
  id: string
  name: string
  surname: string
  email: string
  status: string
  roleNames: string[]
  permissionCodes: string[]
}

export interface StaffInvitation {
  id: string
  email: string
  roleName: string
  permissionCodes: string[]
  expiresAt: string
  expired: boolean
  createdAt: string
}

/** `GET /stores/{id}/staff` (`StaffResponse`). */
export interface Staff {
  storeId: string
  members: StaffMember[]
  invitations: StaffInvitation[]
}

export interface PermissionOption {
  code: string
  description: string
}

/** `StaffInviteRequest`. */
export interface StaffInviteInput {
  email: string
  roleName: string
  permissionCodes: string[]
}

// ------------------------------------------------------------ ventas (analítica)

/** Rangos de días que ofrece la pantalla «Ventas». */
export type SalesRange = 7 | 30 | 90

export interface SalesDailyPoint {
  /** `YYYY-MM-DD` (día local). */
  date: string
  revenue: number
  orders: number
  averageTicket: number
}

/** Pedido que cae en una celda del mapa de calor. */
export interface SalesHeatOrder {
  saleId: string
  orderNumber: number
  createdAt: string
  total: number
  status: OrderStatus
  customerName: string | null
}

/** Celda no vacía del mapa de calor. `day`: 0 = lunes … 6 = domingo; `hour`: 0-23. */
export interface SalesHeatCell {
  day: number
  hour: number
  orders: SalesHeatOrder[]
}

export type SalesFunnelStage = 'RECEIVED' | 'PAID' | 'SHIPPED' | 'DELIVERED'

export type SalesEventKind = 'ORDER' | 'PAYMENT' | 'LOW_STOCK'

export interface SalesEvent {
  id: string
  kind: SalesEventKind
  /** ISO de cuando ocurrió. */
  at: string
  title: string
  detail: string
  /** Ruta relativa a `/portal/:storeId/` (p. ej. `pedidos/{saleId}`), si el evento tiene destino. */
  to?: string
}

/**
 * Analítica de ventas de un rango. Contrato del endpoint recomendado
 * `GET /stores/{id}/analytics/sales?days=7|30|90` (permiso `ORDER_READ`): HOY NO EXISTE en el
 * backend y el servicio HTTP la calcula a partir de `GET /orders` y `GET /products`.
 */
export interface SalesAnalytics {
  range: SalesRange
  generatedAt: string
  totals: { revenue: number; orders: number; averageTicket: number; delivered: number }
  /** Periodo inmediatamente anterior de la misma duración, para comparar. */
  previous: { revenue: number; orders: number; averageTicket: number }
  daily: SalesDailyPoint[]
  heatmap: SalesHeatCell[]
  /** Embudo acumulado: cada etapa cuenta los pedidos que la alcanzaron (sin cancelados/rechazados). */
  funnel: { stage: SalesFunnelStage; count: number }[]
  cancelled: number
}
