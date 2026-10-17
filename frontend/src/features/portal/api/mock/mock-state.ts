import { ApiError, simulateLatency } from '@/lib/api'
import { PLACEHOLDER_IMAGES } from '@/lib/images'
import { getSession } from '@/features/auth/session-store'
import type {
  Branch,
  MyStore,
  OrderDetail,
  OrderItem,
  OrderStatus,
  Plan,
  ProductOptions,
  ProductRow,
  StaffInvitation,
  StaffMember,
  StorePermission,
  StoreProfile,
  Subscription,
  SubscriptionPayment,
  SubscriptionStatus,
  UsageItem,
} from '../types'

/**
 * Base de datos en memoria de los servicios simulados del portal. Imita las reglas del
 * servidor (permisos, cupos, vencimiento, transiciones) para poder probar todos los estados
 * sin backend. No guarda nada fuera de la pestaña.
 *
 * Cómo probar:
 * - Cuenta por defecto: 3 tiendas (prueba con cupo casi lleno, suscripción vencida y en revisión).
 * - Correo que empieza por `sinalmacen`: ninguna tienda (estado vacío de `/portal`).
 * - Correo que empieza por `uno`: una sola tienda (redirige directo).
 * - `?simulateError=1` en la URL: toda petición falla con 503; `?simulateError=403`: 403.
 * - Un `storeId` desconocido en la URL responde 403 "no tienes acceso".
 * - En el diálogo de pago, marca "Simular rechazo" para ver el camino 402 `PAYMENT_REJECTED`.
 */

const DAY = 24 * 60 * 60 * 1000

/** `LocalDateTime` ISO sin zona, relativo a hoy. */
export function isoFromNow(days: number, hour = 10): string {
  const date = new Date(Date.now() + days * DAY)
  date.setHours(hour, 0, 0, 0)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

/** Mismas reglas que `Date.parse` para fechas locales sin zona. */
function time(value: string): number {
  return new Date(value).getTime()
}

// ------------------------------------------------------------------ planes

export const MOCK_PLANS: Plan[] = [
  {
    id: '11111111-0000-4000-8000-000000000001',
    code: 'TRY',
    name: 'Prueba',
    description: 'Un mes para conocer la plataforma con tu catálogo real.',
    monthlyPriceCop: 0,
    currency: 'COP',
    features: ['1 sucursal', 'Hasta 12 productos (límite reducido de la simulación)', '2 usuarios', 'Importación CSV'],
    recommended: false,
    enabled: true,
    maxBranches: 1,
    maxProducts: 12,
    maxUsers: 2,
    capabilities: ['BASIC_ANALYTICS', 'CSV_IMPORT'],
  },
  {
    id: '11111111-0000-4000-8000-000000000002',
    code: 'BASIC',
    name: 'Esencial',
    description: 'Para un almacén con una sola sede que empieza a vender en línea.',
    monthlyPriceCop: 49900,
    currency: 'COP',
    features: ['1 sucursal', 'Hasta 500 productos', '2 usuarios', 'Importación CSV'],
    recommended: false,
    enabled: true,
    maxBranches: 1,
    maxProducts: 500,
    maxUsers: 2,
    capabilities: ['BASIC_ANALYTICS', 'CSV_IMPORT'],
  },
  {
    id: '11111111-0000-4000-8000-000000000003',
    code: 'BUSINESS',
    name: 'Negocio',
    description: 'Para almacenes con varias sedes y equipo propio.',
    monthlyPriceCop: 149900,
    currency: 'COP',
    features: ['Hasta 3 sucursales', 'Hasta 5.000 productos', '10 usuarios', 'Promociones', 'Roles personalizados'],
    recommended: true,
    enabled: true,
    maxBranches: 3,
    maxProducts: 5000,
    maxUsers: 10,
    capabilities: ['BASIC_ANALYTICS', 'CSV_IMPORT', 'PROMOTIONS', 'CUSTOM_ROLES', 'ERP_IMPORT', 'ADVANCED_REPORTS', 'WHATSAPP_NOTIFICATIONS'],
  },
  {
    id: '11111111-0000-4000-8000-000000000005',
    code: 'PRO',
    name: 'Pro',
    description: 'Para operaciones grandes que quieren inventario y facturación conectados.',
    monthlyPriceCop: 299900,
    currency: 'COP',
    features: ['Hasta 10 sucursales', 'Hasta 25.000 productos', '25 usuarios'],
    recommended: false,
    enabled: true,
    maxBranches: 10,
    maxProducts: 25000,
    maxUsers: 25,
    capabilities: [
      'BASIC_ANALYTICS',
      'CSV_IMPORT',
      'PROMOTIONS',
      'CUSTOM_ROLES',
      'ERP_IMPORT',
      'ADVANCED_REPORTS',
      'WHATSAPP_NOTIFICATIONS',
      'ERP_SYNC',
      'E_INVOICING',
      'PRICE_LISTS',
      'PURCHASING',
      'STOCK_ALERTS',
    ],
  },
  {
    id: '11111111-0000-4000-8000-000000000004',
    code: 'ENTERPRISE',
    name: 'Empresarial',
    description: 'Sin límites, con integraciones y acompañamiento.',
    monthlyPriceCop: 0,
    currency: 'COP',
    features: ['Sucursales, productos y usuarios ilimitados', 'Soporte prioritario'],
    recommended: false,
    enabled: true,
    maxBranches: null,
    maxProducts: null,
    maxUsers: null,
    capabilities: [
      'BASIC_ANALYTICS',
      'CSV_IMPORT',
      'PROMOTIONS',
      'CUSTOM_ROLES',
      'ERP_IMPORT',
      'ADVANCED_REPORTS',
      'WHATSAPP_NOTIFICATIONS',
      'ERP_SYNC',
      'E_INVOICING',
      'PRICE_LISTS',
      'PURCHASING',
      'STOCK_ALERTS',
      'API_ACCESS',
      'WEBHOOKS',
      'CUSTOM_DOMAIN',
    ],
  },
]

const TRY_PLAN = MOCK_PLANS[0] as Plan

// ----------------------------------------------------------- opciones de catálogo

export const MOCK_OPTIONS: ProductOptions = {
  manufacturers: [
    { id: 'man-bosch', name: 'Bosch' },
    { id: 'man-brembo', name: 'Brembo' },
    { id: 'man-gates', name: 'Gates' },
    { id: 'man-monroe', name: 'Monroe' },
    { id: 'man-ngk', name: 'NGK' },
    { id: 'man-mann', name: 'Mann-Filter' },
    { id: 'man-sachs', name: 'Sachs' },
    { id: 'man-incolbestos', name: 'Incolbestos' },
  ],
  categories: [
    { id: 'cat-frenos', code: 'FRENOS' },
    { id: 'cat-suspension', code: 'SUSPENSION_Y_DIRECCION' },
    { id: 'cat-motor', code: 'MOTOR_Y_DISTRIBUCION' },
    { id: 'cat-filtros', code: 'FILTROS_Y_LUBRICACION' },
    { id: 'cat-electrico', code: 'ELECTRICO_Y_ENCENDIDO' },
    { id: 'cat-embrague', code: 'EMBRAGUE_Y_TRANSMISION' },
  ],
  compatibilities: [
    { id: 'veh-spark', make: 'Chevrolet', model: 'Spark GT', version: '1.2' },
    { id: 'veh-sail', make: 'Chevrolet', model: 'Sail', version: '1.4' },
    { id: 'veh-logan', make: 'Renault', model: 'Logan', version: '1.6 8V' },
    { id: 'veh-sandero', make: 'Renault', model: 'Sandero', version: '1.6' },
    { id: 'veh-duster', make: 'Renault', model: 'Duster', version: '2.0 4x4' },
    { id: 'veh-mazda2', make: 'Mazda', model: 'Mazda 2', version: '1.5' },
    { id: 'veh-mazda3', make: 'Mazda', model: 'Mazda 3', version: '2.0' },
    { id: 'veh-picanto', make: 'Kia', model: 'Picanto', version: 'Ion 1.0' },
    { id: 'veh-rio', make: 'Kia', model: 'Rio', version: '1.4' },
    { id: 'veh-hilux', make: 'Toyota', model: 'Hilux', version: '2.5 diésel' },
    { id: 'veh-march', make: 'Nissan', model: 'March', version: '1.6' },
    { id: 'veh-swift', make: 'Suzuki', model: 'Swift', version: '1.2' },
  ],
}

// ------------------------------------------------------------------ tiendas

export interface MockStore {
  summary: Omit<MyStore, 'subscription'>
  permissions: StorePermission[]
  profile: StoreProfile
  plan: Plan
  selectedPlan: Plan | null
  trialStartsAt: string | null
  trialEndsAt: string | null
  paidThrough: string | null
  payments: SubscriptionPayment[]
  products: ProductRow[]
  orders: OrderDetail[]
  branches: Branch[]
  members: StaffMember[]
  invitations: StaffInvitation[]
}

export const STORE_A = '6f1c2a3e-0000-4000-8000-000000000001'
export const STORE_B = '6f1c2a3e-0000-4000-8000-000000000002'
export const STORE_C = '6f1c2a3e-0000-4000-8000-000000000003'

const ALL_PERMISSIONS: StorePermission[] = [
  'STORE_READ',
  'STORE_UPDATE',
  'BRANCH_READ',
  'BRANCH_CREATE',
  'BRANCH_UPDATE',
  'BRANCH_DELETE',
  'PRODUCT_READ',
  'PRODUCT_CREATE',
  'PRODUCT_UPDATE',
  'PRODUCT_PUBLISH',
  'PRODUCT_DELETE',
  'PRODUCT_IMAGE_MANAGE',
  'PROMOTION_READ',
  'ORDER_READ',
  'ORDER_UPDATE',
  'SHIPMENT_UPDATE',
  'COLLABORATOR_READ',
  'COLLABORATOR_MANAGE',
]

const BRANCH_A = 'b1000000-0000-4000-8000-00000000000a'
const BRANCH_B = 'b1000000-0000-4000-8000-00000000000b'

function branch(id: string, storeId: string, name: string, phone: string, address: string, city: string, department: string): Branch {
  return {
    id,
    storeId,
    name,
    numberPhone: phone,
    state: 'ACTIVE',
    location: {
      id: `loc-${id}`,
      addressLine: address,
      addressComplement: null,
      city,
      department,
      country: 'Colombia',
      postalCode: null,
      latitude: null,
      longitude: null,
    },
  }
}

interface ProductSeed {
  name: string
  sku: string
  ref: string
  price: number
  qty: number
  man: string
  cat: string
  fits: string[]
  state?: 'ACTIVE' | 'INACTIVE'
  discount?: number
  withImage?: boolean
}

function product(storeId: string, branchId: string, branchName: string, index: number, seed: ProductSeed): ProductRow {
  const discount = seed.discount ?? 0
  return {
    id: `d0000000-0000-4000-8000-${String(storeId.endsWith('1') ? 100 + index : 200 + index).padStart(12, '0')}`,
    storeId,
    branchId,
    branchName,
    name: seed.name,
    sku: seed.sku,
    partNumber: seed.ref,
    description: `${seed.name}, referencia ${seed.ref}. Verifica la compatibilidad con tu vehículo antes de instalar.`,
    price: seed.price,
    effectivePrice: seed.price - discount,
    discountAmount: discount,
    appliedPromotionName: discount ? 'Semana del mecánico' : null,
    quantity: seed.qty,
    manufacturerId: seed.man,
    categoryId: seed.cat,
    state: seed.state ?? 'ACTIVE',
    attributes: {},
    compatibilityIds: seed.fits,
    images: seed.withImage
      ? [
          {
            id: `img-${index}`,
            url: PLACEHOLDER_IMAGES.product,
            isMain: true,
            displayOrder: 0,
            createdAt: isoFromNow(-20),
          },
        ]
      : [],
  }
}

const SEEDS_A: ProductSeed[] = [
  { name: 'Pastillas de freno delanteras', sku: 'PF-001', ref: 'D1234', price: 85000, qty: 14, man: 'man-brembo', cat: 'cat-frenos', fits: ['veh-spark', 'veh-sail'], withImage: true },
  { name: 'Disco de freno ventilado', sku: 'DF-014', ref: 'BD-5521', price: 142000, qty: 6, man: 'man-brembo', cat: 'cat-frenos', fits: ['veh-logan', 'veh-sandero'] },
  { name: 'Amortiguador delantero derecho', sku: 'AM-203', ref: 'G8112', price: 178000, qty: 2, man: 'man-monroe', cat: 'cat-suspension', fits: ['veh-mazda3'], withImage: true },
  { name: 'Kit de distribución con bomba de agua', sku: 'KD-077', ref: 'K015607', price: 389000, qty: 4, man: 'man-gates', cat: 'cat-motor', fits: ['veh-logan', 'veh-sandero', 'veh-duster'], discount: 20000 },
  { name: 'Bujía de iridio (juego x4)', sku: 'BJ-310', ref: 'ILKR7B11', price: 168000, qty: 0, man: 'man-ngk', cat: 'cat-electrico', fits: ['veh-mazda2', 'veh-swift'] },
  { name: 'Filtro de aceite', sku: 'FA-008', ref: 'W712/95', price: 28500, qty: 48, man: 'man-mann', cat: 'cat-filtros', fits: ['veh-spark', 'veh-picanto', 'veh-rio'], withImage: true },
  { name: 'Filtro de aire', sku: 'FA-009', ref: 'C2774', price: 39900, qty: 22, man: 'man-mann', cat: 'cat-filtros', fits: ['veh-sail'] },
  { name: 'Kit de embrague completo', sku: 'KE-120', ref: '620 3098 00', price: 465000, qty: 3, man: 'man-sachs', cat: 'cat-embrague', fits: ['veh-hilux'] },
  { name: 'Rótula inferior', sku: 'RT-045', ref: 'K80301', price: 74000, qty: 9, man: 'man-monroe', cat: 'cat-suspension', fits: ['veh-march', 'veh-rio'], state: 'INACTIVE' },
  { name: 'Bandas de freno traseras', sku: 'BF-052', ref: 'BF-0312', price: 52000, qty: 11, man: 'man-incolbestos', cat: 'cat-frenos', fits: ['veh-picanto'] },
]

const SEEDS_B: ProductSeed[] = [
  { name: 'Amortiguador trasero', sku: 'AT-11', ref: 'MN-3340', price: 210000, qty: 5, man: 'man-monroe', cat: 'cat-suspension', fits: ['veh-duster'] },
  { name: 'Pastillas de freno traseras', sku: 'PT-12', ref: 'D2231', price: 76000, qty: 1, man: 'man-brembo', cat: 'cat-frenos', fits: ['veh-sandero'] },
  { name: 'Terminal de dirección', sku: 'TD-13', ref: 'TD-904', price: 58000, qty: 0, man: 'man-monroe', cat: 'cat-suspension', fits: ['veh-logan'] },
]

function lineItems(products: ProductRow[], picks: [number, number][]): OrderItem[] {
  return picks.flatMap(([index, units]) => {
    const row = products[index]
    return row
      ? [
          {
            inventoryId: `inv-${row.id}`,
            productId: row.id,
            productName: row.name,
            partNumber: row.partNumber,
            units,
            unitPrice: row.effectivePrice,
          },
        ]
      : []
  })
}

function order(
  number: number,
  branchRow: Branch,
  products: ProductRow[],
  picks: [number, number][],
  status: OrderStatus,
  daysAgo: number,
  customer: string,
  city: string,
  department: string,
  address: string,
  options: { shipment?: OrderDetail['shipment']; paymentStatus?: string } = {},
): OrderDetail {
  const items = lineItems(products, picks)
  const subtotal = items.reduce((sum, item) => sum + item.unitPrice * item.units, 0)
  const shippingCost = 12000
  return {
    saleId: `5a1e0000-0000-4000-8000-${String(number).padStart(12, '0')}`,
    orderId: `0d000000-0000-4000-8000-${String(number).padStart(12, '0')}`,
    orderNumber: 10200 + number,
    branchId: branchRow.id,
    branchName: branchRow.name,
    status,
    subtotal,
    shippingCost,
    total: subtotal + shippingCost,
    createdAt: isoFromNow(-daysAgo, 15),
    customerName: customer,
    paymentStatus: options.paymentStatus ?? 'APPROVED',
    paymentMethod: 'Wompi (tarjeta)',
    shipment: options.shipment ?? null,
    items,
    deliveryAddress: { addressLine: address, addressComplement: null, city, department, country: 'Colombia', postalCode: null },
  }
}

function shipment(status: NonNullable<OrderDetail['shipment']>['status'], carrier: string, tracking: string): NonNullable<OrderDetail['shipment']> {
  const events: { status: string; notes: string | null; occurredAt: string }[] = [
    { status: 'PREPARING', notes: 'Envío registrado', occurredAt: isoFromNow(-3, 9) },
  ]
  if (status !== 'PREPARING') events.push({ status: 'DISPATCHED', notes: null, occurredAt: isoFromNow(-2, 11) })
  if (status === 'DELIVERED') events.push({ status: 'DELIVERED', notes: 'Recibido por el cliente', occurredAt: isoFromNow(-1, 16) })
  return {
    id: `sh-${tracking}`,
    carrier,
    trackingNumber: tracking,
    status,
    shippedAt: status === 'PREPARING' ? null : isoFromNow(-2, 11),
    deliveredAt: status === 'DELIVERED' ? isoFromNow(-1, 16) : null,
    events,
  }
}

function buildStoreA(): MockStore {
  const branchA = branch(BRANCH_A, STORE_A, 'Sede Calle 30', '3015550142', 'Calle 30 # 24-18', 'Bogotá', 'Cundinamarca')
  const products = SEEDS_A.map((seed, index) => product(STORE_A, BRANCH_A, branchA.name, index + 1, seed))
  const orders = [
    order(1, branchA, products, [[0, 2]], 'PENDING', 0, 'Camila Rodríguez Pardo', 'Bogotá', 'Cundinamarca', 'Carrera 15 # 85-32, apto 402', { paymentStatus: 'PENDING' }),
    order(2, branchA, products, [[5, 3], [6, 1]], 'PENDING', 1, 'Andrés Felipe Gómez', 'Soacha', 'Cundinamarca', 'Calle 13 # 7-45'),
    order(3, branchA, products, [[3, 1]], 'CONFIRMED', 2, 'Taller Mecánico El Pilar', 'Bogotá', 'Cundinamarca', 'Avenida Boyacá # 56-12'),
    order(4, branchA, products, [[1, 2], [0, 2]], 'PREPARING', 3, 'Luisa Fernanda Castaño', 'Chía', 'Cundinamarca', 'Vereda Fonquetá, km 2'),
    order(5, branchA, products, [[7, 1]], 'SHIPPED', 5, 'Jhon Alexander Mejía', 'Medellín', 'Antioquia', 'Carrera 70 # 44-10', { shipment: shipment('DISPATCHED', 'Servientrega', 'SV88421907') }),
    order(6, branchA, products, [[2, 2]], 'DELIVERED', 9, 'Marcela Ortiz Vargas', 'Cali', 'Valle del Cauca', 'Calle 5 # 38-70', { shipment: shipment('DELIVERED', 'Coordinadora', 'CD55120038') }),
    order(7, branchA, products, [[9, 4]], 'CANCELLED', 12, 'Sebastián Ramírez', 'Bogotá', 'Cundinamarca', 'Transversal 93 # 53-32', { paymentStatus: 'DECLINED' }),
  ]
  return {
    summary: {
      storeId: STORE_A,
      businessName: 'Repuestos La 30',
      logoUrl: null,
      applicationStatus: 'ACTIVE',
      isOwner: true,
      roleNames: ['Propietario'],
    },
    permissions: ALL_PERMISSIONS,
    profile: {
      id: STORE_A,
      businessName: 'Repuestos La 30',
      nit: '900123456-7',
      businessEmail: 'ventas@repuestoslala30.co',
      state: 'ACTIVE',
      description: 'Repuestos para carros asiáticos y europeos con más de 15 años en la Calle 30.',
      logoUrl: null,
      bannerUrl: null,
    },
    plan: TRY_PLAN,
    selectedPlan: null,
    trialStartsAt: isoFromNow(-18),
    trialEndsAt: isoFromNow(12),
    paidThrough: null,
    payments: [],
    products,
    orders,
    branches: [branchA],
    members: [
      { id: 'm-1', name: 'Marta', surname: 'Quintero', email: 'marta@repuestoslala30.co', status: 'ACTIVE', roleNames: ['Propietario'], permissionCodes: ALL_PERMISSIONS },
    ],
    invitations: [],
  }
}

function buildStoreB(): MockStore {
  const branchRow = branch(BRANCH_B, STORE_B, 'Sede Norte', '6045550177', 'Carrera 43A # 34-95', 'Medellín', 'Antioquia')
  const products = SEEDS_B.map((seed, index) => product(STORE_B, BRANCH_B, branchRow.name, index + 1, seed))
  return {
    summary: {
      storeId: STORE_B,
      businessName: 'Frenos y Suspensión del Norte',
      logoUrl: null,
      applicationStatus: 'ACTIVE',
      isOwner: false,
      roleNames: ['Administrador'],
    },
    permissions: ['STORE_READ', 'BRANCH_READ', 'PRODUCT_READ', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_PUBLISH', 'ORDER_READ', 'ORDER_UPDATE', 'COLLABORATOR_READ'],
    profile: {
      id: STORE_B,
      businessName: 'Frenos y Suspensión del Norte',
      nit: '901456789-1',
      businessEmail: 'contacto@frenosnorte.co',
      state: 'ACTIVE',
      description: null,
      logoUrl: null,
      bannerUrl: null,
    },
    plan: TRY_PLAN,
    selectedPlan: MOCK_PLANS[2] ?? null,
    trialStartsAt: isoFromNow(-45),
    trialEndsAt: isoFromNow(-10),
    paidThrough: null,
    payments: [],
    products,
    orders: [],
    branches: [branchRow],
    members: [
      { id: 'm-9', name: 'Ricardo', surname: 'Herrera', email: 'ricardo@frenosnorte.co', status: 'ACTIVE', roleNames: ['Propietario'], permissionCodes: ALL_PERMISSIONS },
      { id: 'm-10', name: 'Paola', surname: 'Duque', email: 'paola@frenosnorte.co', status: 'ACTIVE', roleNames: ['Administrador'], permissionCodes: ['STORE_READ', 'PRODUCT_READ'] },
    ],
    invitations: [],
  }
}

function buildStoreC(): MockStore {
  return {
    summary: {
      storeId: STORE_C,
      businessName: 'Autopartes Medellín Centro',
      logoUrl: null,
      applicationStatus: 'PENDING_REVIEW',
      isOwner: true,
      roleNames: ['Propietario'],
    },
    permissions: [],
    profile: {
      id: STORE_C,
      businessName: 'Autopartes Medellín Centro',
      nit: '901222333-4',
      businessEmail: 'hola@autopartesmedellin.co',
      state: 'PENDING',
      description: null,
      logoUrl: null,
      bannerUrl: null,
    },
    plan: TRY_PLAN,
    selectedPlan: null,
    trialStartsAt: null,
    trialEndsAt: null,
    paidThrough: null,
    payments: [],
    products: [],
    orders: [],
    branches: [],
    members: [],
    invitations: [],
  }
}

const stores = new Map<string, MockStore>([
  [STORE_A, buildStoreA()],
  [STORE_B, buildStoreB()],
  [STORE_C, buildStoreC()],
])

// ------------------------------------------------------------ reglas de servidor

export function forbidden(message = 'No tienes acceso a este almacén.'): ApiError {
  return new ApiError(message, { status: 403, code: 'FORBIDDEN' })
}

/** Latencia y fallos simulados. Úsala al inicio de cada método. */
export async function simulateNetwork(ms = 450): Promise<void> {
  await simulateLatency(ms)
  const flag = new URLSearchParams(window.location.search).get('simulateError')
  if (flag === '403') throw forbidden()
  if (flag !== null) throw new ApiError('Error simulado: el servidor no respondió.', { status: 503, code: 'SERVICE_UNAVAILABLE' })
}

/** Tienda por id; un id desconocido o sin sesión se comporta como el servidor (403). */
export function getStore(storeId: string): MockStore {
  if (!getSession()) throw new ApiError('Debes iniciar sesión.', { status: 401, code: 'UNAUTHORIZED' })
  const store = stores.get(storeId)
  if (!store) throw forbidden()
  return store
}

export function requirePermission(store: MockStore, code: StorePermission): void {
  if (!store.permissions.includes(code)) {
    throw forbidden('Tu rol no tiene permiso para hacer esta acción.')
  }
}

export function requireOwner(store: MockStore): void {
  if (!store.summary.isOwner) throw forbidden('Solo el propietario del almacén puede hacer esto.')
}

/** Cuentas que ve cada tipo de correo (ver la descripción de este archivo). */
export function storesForSession(): MockStore[] {
  const email = getSession()?.user.email ?? ''
  // `cliente...` y `admin...` simulan cuentas sin rol de almacén (ver mock-auth-service).
  if (email.startsWith('sinalmacen') || email.startsWith('cliente') || email.startsWith('admin')) return []
  if (email.startsWith('uno')) return [stores.get(STORE_A) as MockStore]
  return [...stores.values()]
}

export function statusOf(store: MockStore): SubscriptionStatus {
  if (store.summary.applicationStatus !== 'ACTIVE') return 'PENDING_SETUP'
  const now = Date.now()
  if (store.paidThrough && time(store.paidThrough) > now) return 'ACTIVE'
  if (store.trialEndsAt && time(store.trialEndsAt) > now) return 'TRIALING'
  return 'EXPIRED'
}

export function validThrough(store: MockStore): string | null {
  const status = statusOf(store)
  if (status === 'ACTIVE') return store.paidThrough
  return store.trialEndsAt
}

export function usageOf(store: MockStore): Record<'branches' | 'products' | 'users', UsageItem> {
  const productIds = new Set(store.products.map((row) => row.id))
  const pendingInvites = store.invitations.filter((invitation) => !invitation.expired).length
  return {
    branches: { used: store.branches.filter((row) => row.state === 'ACTIVE').length, limit: store.plan.maxBranches },
    products: { used: productIds.size, limit: store.plan.maxProducts },
    users: { used: store.members.length + pendingInvites, limit: store.plan.maxUsers },
  }
}

export function subscriptionOf(store: MockStore): Subscription {
  const status = statusOf(store)
  const valid = validThrough(store)
  const daysRemaining = valid ? Math.max(0, Math.ceil((time(valid) - Date.now()) / DAY)) : null
  return {
    storeId: store.summary.storeId,
    status,
    plan: store.plan,
    selectedPlan: store.selectedPlan,
    trialStartsAt: store.trialStartsAt,
    trialEndsAt: store.trialEndsAt,
    paidThrough: store.paidThrough,
    validThrough: valid,
    daysRemaining,
    usage: usageOf(store),
    capabilities: store.plan.capabilities,
    paymentMode: 'SIMULATED',
    canManage: store.summary.isOwner,
    planChangeAllowed: status !== 'ACTIVE',
  }
}

export function summaryOf(store: MockStore): MyStore {
  const subscription = subscriptionOf(store)
  return {
    ...store.summary,
    subscription: store.summary.applicationStatus === 'PENDING_REVIEW' || store.summary.applicationStatus === 'REJECTED'
      ? null
      : {
          status: subscription.status,
          planCode: subscription.plan.code,
          planName: subscription.plan.name,
          validThrough: subscription.validThrough,
        },
  }
}

/** 409 `SELLER_SUBSCRIPTION_EXPIRED` (crear, publicar, invitar y agregar sucursales). */
export function requireActiveSubscription(store: MockStore): void {
  if (statusOf(store) === 'EXPIRED') {
    throw new ApiError('Tu suscripción venció. Renueva tu plan para crear o publicar.', {
      status: 409,
      code: 'SELLER_SUBSCRIPTION_EXPIRED',
      details: { expiredAt: store.trialEndsAt ?? store.paidThrough },
    })
  }
}

/** 409 `SELLER_PLAN_LIMIT_REACHED` con `details` como el servidor (plan §4.2). */
export function requireCapacity(store: MockStore, capacity: 'PRODUCTS' | 'BRANCHES' | 'USERS', requested = 1): void {
  const usage = usageOf(store)
  const item = usage[capacity === 'PRODUCTS' ? 'products' : capacity === 'BRANCHES' ? 'branches' : 'users']
  if (item.limit !== null && item.used + requested > item.limit) {
    throw new ApiError('Llegaste al límite de tu plan para esta función.', {
      status: 409,
      code: 'SELLER_PLAN_LIMIT_REACHED',
      details: { capacity, limit: item.limit, used: item.used, requested, planCode: store.plan.code },
    })
  }
}

export function requireCapability(store: MockStore, capability: string): void {
  if (!store.plan.capabilities.includes(capability)) {
    throw new ApiError('Tu plan no incluye esta función.', {
      status: 409,
      code: 'SELLER_PLAN_FEATURE_UNAVAILABLE',
      details: { capability, planCode: store.plan.code },
    })
  }
}

export function newId(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`
}
