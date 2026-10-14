/**
 * Datos simulados para desarrollar la UI sin backend.
 * Marcas de repuestos y vehículos reales del mercado colombiano; las tiendas son ficticias.
 * Los precios son de referencia en COP y no representan ofertas reales.
 */
import { PLACEHOLDER_IMAGES } from '@/lib/images'
import type { AvailabilityStatus, Brand, Category, Product, StoreSummary, VehicleFit } from '../types'

export const MOCK_CATEGORIES: Category[] = [
  { id: 'cat-frenos', slug: 'frenos', name: 'Frenos', description: 'Pastillas, discos, bandas y líquido de frenos.' },
  { id: 'cat-suspension', slug: 'suspension', name: 'Suspensión y dirección', description: 'Amortiguadores, rótulas, terminales y bujes.' },
  { id: 'cat-motor', slug: 'motor', name: 'Motor y distribución', description: 'Kits de distribución, bombas de agua y correas.' },
  { id: 'cat-filtros', slug: 'filtros', name: 'Filtros y lubricación', description: 'Filtros de aceite, aire, combustible y cabina, y aceites.' },
  { id: 'cat-electrico', slug: 'electrico', name: 'Eléctrico y encendido', description: 'Bujías, bobinas, baterías, alternadores y sensores.' },
  { id: 'cat-refrigeracion', slug: 'refrigeracion', name: 'Refrigeración', description: 'Radiadores, termostatos y electroventiladores.' },
  { id: 'cat-embrague', slug: 'embrague', name: 'Embrague y transmisión', description: 'Kits de embrague, collarines y discos.' },
  { id: 'cat-escape', slug: 'escape', name: 'Escape', description: 'Silenciadores, catalizadores y tubería.' },
]

export const MOCK_BRANDS: Brand[] = [
  { id: 'bosch', name: 'Bosch' },
  { id: 'denso', name: 'Denso' },
  { id: 'gabriel', name: 'Gabriel' },
  { id: 'gates', name: 'Gates' },
  { id: 'incolbestos', name: 'Incolbestos' },
  { id: 'luk', name: 'LuK' },
  { id: 'mann', name: 'Mann-Filter' },
  { id: 'mobil', name: 'Mobil' },
  { id: 'monroe', name: 'Monroe' },
  { id: 'moog', name: 'Moog' },
  { id: 'ngk', name: 'NGK' },
  { id: 'sachs', name: 'Sachs' },
  { id: 'valeo', name: 'Valeo' },
  { id: 'walker', name: 'Walker' },
]

const VEHICLES = {
  spark: { id: 'veh-spark', make: 'Chevrolet', model: 'Spark GT', version: '1.2', yearFrom: 2011, yearTo: 2018 },
  sail: { id: 'veh-sail', make: 'Chevrolet', model: 'Sail', version: '1.4', yearFrom: 2013, yearTo: 2020 },
  logan: { id: 'veh-logan', make: 'Renault', model: 'Logan', version: '1.6 8V', yearFrom: 2008, yearTo: 2020 },
  sandero: { id: 'veh-sandero', make: 'Renault', model: 'Sandero', version: '1.6', yearFrom: 2009, yearTo: 2020 },
  duster: { id: 'veh-duster', make: 'Renault', model: 'Duster', version: '2.0 4x4', yearFrom: 2012, yearTo: 2022 },
  mazda2: { id: 'veh-mazda2', make: 'Mazda', model: 'Mazda 2', version: '1.5', yearFrom: 2015, yearTo: 2022 },
  mazda3: { id: 'veh-mazda3', make: 'Mazda', model: 'Mazda 3', version: '2.0', yearFrom: 2014, yearTo: 2019 },
  picanto: { id: 'veh-picanto', make: 'Kia', model: 'Picanto', version: 'Ion 1.0', yearFrom: 2011, yearTo: 2017 },
  rio: { id: 'veh-rio', make: 'Kia', model: 'Rio', version: '1.4', yearFrom: 2012, yearTo: 2017 },
  hilux: { id: 'veh-hilux', make: 'Toyota', model: 'Hilux', version: '2.5 diésel', yearFrom: 2006, yearTo: 2015 },
  march: { id: 'veh-march', make: 'Nissan', model: 'March', version: '1.6', yearFrom: 2012, yearTo: 2020 },
  swift: { id: 'veh-swift', make: 'Suzuki', model: 'Swift', version: '1.2', yearFrom: 2018, yearTo: 2023 },
} satisfies Record<string, VehicleFit>

type VehicleKey = keyof typeof VEHICLES

export const MOCK_STORES: StoreSummary[] = [
  { id: 'store-la13', name: 'Repuestos La 13', city: 'Bogotá', recommended: true },
  { id: 'store-barrio-colombia', name: 'Autopartes Barrio Colombia', city: 'Medellín' },
  { id: 'store-valle', name: 'Frenos y Embragues del Valle', city: 'Cali', recommended: true },
  { id: 'store-costa', name: 'Motorepuestos La Costa', city: 'Barranquilla' },
  { id: 'store-santander', name: 'Lubricentro Santander', city: 'Bucaramanga', recommended: true },
  { id: 'store-eje', name: 'Repuestos Eje Cafetero', city: 'Pereira' },
]

const BRANCHES: Record<string, string> = {
  'store-la13': 'Sede Calle 13',
  'store-barrio-colombia': 'Sede Barrio Colombia',
  'store-valle': 'Sede San Nicolás',
  'store-costa': 'Sede Centro',
  'store-santander': 'Sede Cabecera',
  'store-eje': 'Sede Circunvalar',
}

type StoreKey = 'la13' | 'barrio' | 'valle' | 'costa' | 'santander' | 'eje'
const STORE_IDS: Record<StoreKey, string> = {
  la13: 'store-la13',
  barrio: 'store-barrio-colombia',
  valle: 'store-valle',
  costa: 'store-costa',
  santander: 'store-santander',
  eje: 'store-eje',
}

interface Seed {
  name: string
  ref: string
  category: string
  brand: string
  price: number
  store: StoreKey
  fits: VehicleKey[]
  stock: number
  discount?: number
  promo?: string
  specs?: Record<string, string>
  description?: string
}

const SEEDS: Seed[] = [
  // Frenos
  { name: 'Pastillas de freno delanteras', ref: 'IB-1023', category: 'frenos', brand: 'incolbestos', price: 68900, store: 'la13', fits: ['spark', 'picanto'], stock: 24, specs: { Posición: 'Delantera', Material: 'Semimetálica', Juego: '4 pastillas' } },
  { name: 'Pastillas de freno delanteras cerámicas', ref: '0986BB0812', category: 'frenos', brand: 'bosch', price: 132000, store: 'valle', fits: ['logan', 'sandero'], stock: 9, discount: 19800, promo: 'Semana del freno', specs: { Posición: 'Delantera', Material: 'Cerámica', Juego: '4 pastillas' } },
  { name: 'Discos de freno delanteros ventilados (par)', ref: 'BD-4471', category: 'frenos', brand: 'bosch', price: 389000, store: 'barrio', fits: ['duster'], stock: 3, specs: { Posición: 'Delantera', Tipo: 'Ventilado', Diámetro: '280 mm', Unidades: '2' } },
  { name: 'Bandas de freno traseras', ref: 'IB-2210', category: 'frenos', brand: 'incolbestos', price: 59900, store: 'costa', fits: ['spark', 'sail'], stock: 15, specs: { Posición: 'Trasera', Diámetro: '180 mm' } },
  { name: 'Líquido de frenos DOT 4 500 ml', ref: '1987479106', category: 'frenos', brand: 'bosch', price: 28500, store: 'santander', fits: [], stock: 40, specs: { Norma: 'DOT 4', Contenido: '500 ml' }, description: 'Líquido de frenos sintético de alto punto de ebullición. Aplica para la mayoría de vehículos con sistema hidráulico DOT 4; confirma la norma en el manual de tu carro.' },
  { name: 'Pastillas de freno delanteras', ref: 'IB-1188', category: 'frenos', brand: 'incolbestos', price: 84500, store: 'eje', fits: ['mazda2'], stock: 0, specs: { Posición: 'Delantera', Material: 'Semimetálica' } },
  { name: 'Pastillas de freno delanteras', ref: 'IB-1301', category: 'frenos', brand: 'incolbestos', price: 112000, store: 'la13', fits: ['hilux'], stock: 6, specs: { Posición: 'Delantera', Material: 'Semimetálica', Juego: '4 pastillas' } },

  // Suspensión y dirección
  { name: 'Amortiguador delantero a gas', ref: 'MR-72315', category: 'suspension', brand: 'monroe', price: 285000, store: 'barrio', fits: ['mazda3'], stock: 5, specs: { Posición: 'Delantera', Tipo: 'Gas', Lado: 'Derecho' } },
  { name: 'Amortiguador trasero', ref: 'GB-G63411', category: 'suspension', brand: 'gabriel', price: 168000, store: 'la13', fits: ['logan', 'sandero'], stock: 12, specs: { Posición: 'Trasera', Tipo: 'Hidráulico' } },
  { name: 'Terminal de dirección', ref: 'ES-80995', category: 'suspension', brand: 'moog', price: 64000, store: 'costa', fits: ['spark'], stock: 2, specs: { Lado: 'Izquierdo', Rosca: 'M12 x 1.25' } },
  { name: 'Rótula de suspensión inferior', ref: 'K-500212', category: 'suspension', brand: 'moog', price: 89000, store: 'santander', fits: ['sail'], stock: 8, specs: { Posición: 'Inferior' } },
  { name: 'Amortiguador trasero a gas', ref: 'MR-37311', category: 'suspension', brand: 'monroe', price: 310000, store: 'valle', fits: ['hilux'], stock: 4, discount: 31000, promo: 'Precio de temporada', specs: { Posición: 'Trasera', Tipo: 'Gas' } },
  { name: 'Buje de tijera delantero', ref: 'K-201145', category: 'suspension', brand: 'moog', price: 38000, store: 'eje', fits: ['spark', 'picanto'], stock: 30, specs: { Material: 'Caucho' } },
  { name: 'Amortiguador delantero', ref: 'GB-G35210', category: 'suspension', brand: 'gabriel', price: 198000, store: 'barrio', fits: ['march'], stock: 7, specs: { Posición: 'Delantera', Tipo: 'Hidráulico' } },

  // Motor y distribución
  { name: 'Kit de distribución con bomba de agua', ref: 'TCKWP-306', category: 'motor', brand: 'gates', price: 415000, store: 'la13', fits: ['logan', 'sandero'], stock: 6, specs: { Contenido: 'Correa, tensor y bomba de agua', Motor: 'K7M 1.6 8V' } },
  { name: 'Bomba de agua', ref: 'WP-1471', category: 'motor', brand: 'gates', price: 178000, store: 'santander', fits: ['sail'], stock: 3, specs: { Material: 'Aluminio' } },
  { name: 'Correa de accesorios', ref: '6PK1195', category: 'motor', brand: 'gates', price: 62000, store: 'barrio', fits: ['mazda2'], stock: 18, specs: { Canales: '6', Longitud: '1.195 mm' } },
  { name: 'Kit de distribución', ref: 'TCK-1590', category: 'motor', brand: 'gates', price: 268000, store: 'costa', fits: ['spark'], stock: 0, specs: { Contenido: 'Correa y tensor' } },
  { name: 'Correa de distribución', ref: 'T-41120', category: 'motor', brand: 'gates', price: 96000, store: 'eje', fits: ['picanto'], stock: 10, specs: { Dientes: '113' } },

  // Filtros y lubricación
  { name: 'Filtro de aceite', ref: 'W 67/1', category: 'filtros', brand: 'mann', price: 18900, store: 'santander', fits: ['spark', 'sail', 'picanto'], stock: 60, specs: { Tipo: 'Roscado' } },
  { name: 'Filtro de aire de motor', ref: 'C 26 017', category: 'filtros', brand: 'mann', price: 52000, store: 'barrio', fits: ['mazda3'], stock: 14, specs: { Tipo: 'Panel' } },
  { name: 'Filtro de combustible', ref: 'F026403011', category: 'filtros', brand: 'bosch', price: 39000, store: 'la13', fits: ['logan', 'sandero', 'duster'], stock: 22 },
  { name: 'Filtro de cabina con carbón activado', ref: 'CUK 2442', category: 'filtros', brand: 'mann', price: 45000, store: 'valle', fits: ['mazda2', 'mazda3'], stock: 2, specs: { Tipo: 'Carbón activado' } },
  { name: 'Aceite de motor 10W-30 semisintético (cuarto)', ref: 'MS2000-1030', category: 'filtros', brand: 'mobil', price: 42000, store: 'santander', fits: [], stock: 80, specs: { Viscosidad: '10W-30', Contenido: '946 ml', Tipo: 'Semisintético' } },
  { name: 'Aceite de motor 5W-30 sintético (galón)', ref: 'M1-530-GL', category: 'filtros', brand: 'mobil', price: 189000, store: 'la13', fits: [], stock: 11, discount: 24000, promo: 'Combo cambio de aceite', specs: { Viscosidad: '5W-30', Contenido: '3,78 l', Tipo: 'Sintético' } },
  { name: 'Filtro de aceite', ref: 'W 610/3', category: 'filtros', brand: 'mann', price: 21500, store: 'costa', fits: ['march', 'swift'], stock: 35 },

  // Eléctrico y encendido
  { name: 'Bujías de iridio (juego x4)', ref: 'IZFR6K11', category: 'electrico', brand: 'ngk', price: 148000, store: 'barrio', fits: ['spark', 'march'], stock: 9, specs: { Material: 'Iridio', Unidades: '4' } },
  { name: 'Bujía de níquel', ref: 'BKR6E', category: 'electrico', brand: 'ngk', price: 16500, store: 'santander', fits: ['logan', 'sandero', 'sail'], stock: 120, specs: { Material: 'Níquel', Unidades: '1' } },
  { name: 'Bobina de encendido', ref: '0221504470', category: 'electrico', brand: 'bosch', price: 212000, store: 'la13', fits: ['sandero', 'logan'], stock: 4 },
  { name: 'Alternador 12 V 110 A', ref: 'TG11C032', category: 'electrico', brand: 'valeo', price: 890000, store: 'valle', fits: ['duster'], stock: 1, specs: { Voltaje: '12 V', Corriente: '110 A' } },
  { name: 'Motor de arranque', ref: '428000-3360', category: 'electrico', brand: 'denso', price: 1150000, store: 'barrio', fits: ['hilux'], stock: 2, specs: { Voltaje: '12 V', Potencia: '2,0 kW' } },
  { name: 'Sensor de oxígeno', ref: '0258006537', category: 'electrico', brand: 'bosch', price: 235000, store: 'eje', fits: ['spark', 'sail'], stock: 5, specs: { Cables: '4' } },
  { name: 'Batería 12 V 45 Ah', ref: 'S4-45D', category: 'electrico', brand: 'bosch', price: 365000, store: 'costa', fits: ['spark', 'picanto', 'march', 'swift'], stock: 7, specs: { Voltaje: '12 V', Capacidad: '45 Ah', Garantía: '12 meses' } },

  // Refrigeración
  { name: 'Radiador de motor', ref: 'RA-734301', category: 'refrigeracion', brand: 'valeo', price: 365000, store: 'santander', fits: ['spark'], stock: 3, specs: { Material: 'Aluminio y plástico' } },
  { name: 'Termostato con carcasa', ref: 'TH-82092', category: 'refrigeracion', brand: 'gates', price: 58000, store: 'la13', fits: ['sail'], stock: 16, specs: { Apertura: '87 °C' } },
  { name: 'Electroventilador', ref: '696257', category: 'refrigeracion', brand: 'valeo', price: 420000, store: 'valle', fits: ['logan'], stock: 0 },
  { name: 'Radiador de motor', ref: 'DRM50080', category: 'refrigeracion', brand: 'denso', price: 590000, store: 'barrio', fits: ['mazda3'], stock: 2 },

  // Embrague y transmisión
  { name: 'Kit de embrague', ref: '618 3042 00', category: 'embrague', brand: 'luk', price: 465000, store: 'valle', fits: ['spark'], stock: 5, specs: { Contenido: 'Disco, prensa y collarín', Diámetro: '190 mm' } },
  { name: 'Kit de embrague', ref: '3000 990 361', category: 'embrague', brand: 'sachs', price: 890000, store: 'barrio', fits: ['mazda3'], stock: 2, discount: 89000, promo: 'Semana del embrague', specs: { Contenido: 'Disco, prensa y collarín' } },
  { name: 'Kit de embrague', ref: '826 856', category: 'embrague', brand: 'valeo', price: 780000, store: 'la13', fits: ['duster'], stock: 4, specs: { Contenido: 'Disco, prensa y collarín', Diámetro: '215 mm' } },
  { name: 'Collarín de embrague', ref: '500 0391 10', category: 'embrague', brand: 'luk', price: 98000, store: 'eje', fits: ['logan', 'sandero'], stock: 9 },

  // Escape
  { name: 'Silenciador trasero', ref: 'WK-21845', category: 'escape', brand: 'walker', price: 245000, store: 'costa', fits: ['logan'], stock: 3 },
  { name: 'Catalizador', ref: 'WK-28431', category: 'escape', brand: 'walker', price: 690000, store: 'la13', fits: ['spark'], stock: 1, specs: { Norma: 'Euro IV' } },
  { name: 'Silenciador intermedio', ref: 'WK-22013', category: 'escape', brand: 'walker', price: 198000, store: 'santander', fits: ['sail'], stock: 6 },

  // Misma referencia publicada por varios almacenes (comparación de precios).
  // Van al final para no cambiar los id de los repuestos anteriores.
  { name: 'Pastillas de freno delanteras', ref: 'IB-1023', category: 'frenos', brand: 'incolbestos', price: 72500, store: 'barrio', fits: ['spark', 'picanto'], stock: 12, specs: { Posición: 'Delantera', Material: 'Semimetálica', Juego: '4 pastillas' } },
  { name: 'Pastillas de freno delanteras', ref: 'IB-1023', category: 'frenos', brand: 'incolbestos', price: 64900, store: 'valle', fits: ['spark', 'picanto'], stock: 6, specs: { Posición: 'Delantera', Material: 'Semimetálica', Juego: '4 pastillas' } },
  { name: 'Pastillas de freno delanteras', ref: 'IB-1023', category: 'frenos', brand: 'incolbestos', price: 69900, store: 'santander', fits: ['spark', 'picanto'], stock: 18, specs: { Posición: 'Delantera', Material: 'Semimetálica', Juego: '4 pastillas' } },
  { name: 'Pastillas de freno delanteras', ref: 'IB-1023', category: 'frenos', brand: 'incolbestos', price: 75000, store: 'costa', fits: ['spark', 'picanto'], stock: 3, specs: { Posición: 'Delantera', Material: 'Semimetálica', Juego: '4 pastillas' } },
  { name: 'Pastillas de freno delanteras', ref: 'IB-1023', category: 'frenos', brand: 'incolbestos', price: 66500, store: 'eje', fits: ['spark', 'picanto'], stock: 8, specs: { Posición: 'Delantera', Material: 'Semimetálica', Juego: '4 pastillas' } },
]

function availabilityStatus(stock: number): AvailabilityStatus {
  if (stock <= 0) return 'out_of_stock'
  return stock <= 3 ? 'low_stock' : 'in_stock'
}

const categoryBySlug = new Map(MOCK_CATEGORIES.map((category) => [category.slug, category]))
const brandById = new Map(MOCK_BRANDS.map((brand) => [brand.id, brand]))
const storeById = new Map(MOCK_STORES.map((store) => [store.id, store]))

export const MOCK_PRODUCTS: Product[] = SEEDS.map((seed, index) => {
  const id = `rep-${String(index + 1).padStart(3, '0')}`
  const storeId = STORE_IDS[seed.store]
  const category = categoryBySlug.get(seed.category)
  const brand = brandById.get(seed.brand)
  const fits = seed.fits.map((key) => VEHICLES[key])
  const fitText = fits.map((fit) => `${fit.make} ${fit.model}`).join(', ')
  return {
    id,
    listingKey: `${id}:${storeId}`,
    name: seed.name,
    partNumber: seed.ref,
    description:
      seed.description ??
      `${seed.name} ${brand?.name ?? ''}, referencia ${seed.ref}.${fitText ? ` Aplica para ${fitText}.` : ''} Verifica la referencia con la tienda antes de comprar.`,
    price: seed.price - (seed.discount ?? 0),
    listPrice: seed.discount ? seed.price : undefined,
    discountAmount: seed.discount,
    promotionName: seed.promo,
    images: [PLACEHOLDER_IMAGES.product, PLACEHOLDER_IMAGES.category],
    category,
    brand,
    store: storeById.get(storeId),
    availability: { status: availabilityStatus(seed.stock), quantity: seed.stock, branchName: BRANCHES[storeId] },
    compatibilities: fits,
    specifications: [
      ...(brand ? [{ label: 'Marca', value: brand.name }] : []),
      { label: 'Referencia', value: seed.ref },
      ...Object.entries(seed.specs ?? {}).map(([label, value]) => ({ label, value })),
    ],
  }
})
