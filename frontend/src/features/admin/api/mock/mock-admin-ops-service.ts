import { ApiError, simulateLatency } from '@/lib/api'
import type { AdminCategory, AdminManufacturer, AdminOpsService, AdminPlan, PlanInput } from '../ops-types'

/**
 * Consola ROOT simulada: planes y catálogo maestro en memoria, con las mismas reglas que el servidor
 * (códigos únicos). Se reinicia al recargar.
 */

const PLANS: AdminPlan[] = [
  {
    id: 'plan-basic',
    code: 'BASIC',
    name: 'Básico',
    description: 'Para empezar a vender en el marketplace.',
    monthlyPriceCop: 0,
    currency: 'COP',
    features: ['1 sucursal', 'Hasta 100 productos', 'Resumen de ventas'],
    recommended: false,
    enabled: true,
    maxBranches: 1,
    maxProducts: 100,
    maxUsers: 2,
    capabilities: ['BASIC_ANALYTICS'],
  },
  {
    id: 'plan-pro',
    code: 'PRO',
    name: 'Profesional',
    description: 'Almacenes con varias sedes y equipo.',
    monthlyPriceCop: 149_000,
    currency: 'COP',
    features: ['5 sucursales', 'Hasta 2.000 productos', 'Promociones', 'Importación CSV'],
    recommended: true,
    enabled: true,
    maxBranches: 5,
    maxProducts: 2000,
    maxUsers: 10,
    capabilities: ['BASIC_ANALYTICS', 'PROMOTIONS', 'CSV_IMPORT', 'ADVANCED_REPORTS'],
  },
  {
    id: 'plan-enterprise',
    code: 'ENTERPRISE',
    name: 'Empresarial',
    description: 'Cadenas y distribuidores con integraciones.',
    monthlyPriceCop: 459_000,
    currency: 'COP',
    features: ['Sucursales ilimitadas', 'Productos ilimitados', 'Integraciones y API'],
    recommended: false,
    enabled: true,
    maxBranches: null,
    maxProducts: null,
    maxUsers: null,
    capabilities: ['BASIC_ANALYTICS', 'PROMOTIONS', 'CSV_IMPORT', 'ADVANCED_REPORTS', 'CUSTOM_ROLES', 'INTEGRATIONS', 'API_ACCESS'],
  },
]

const CATEGORIES: AdminCategory[] = ['FRENOS', 'SUSPENSION', 'MOTOR', 'ELECTRICO', 'FILTROS', 'LUBRICANTES', 'CARROCERIA'].map((code, index) => ({
  id: `cat-${index + 1}`,
  code,
}))

const MANUFACTURERS: AdminManufacturer[] = [
  { id: 'mf-1', name: 'Bosch', approved: true, suggestedByStoreId: null },
  { id: 'mf-2', name: 'Brembo', approved: true, suggestedByStoreId: null },
  { id: 'mf-3', name: 'Monroe', approved: true, suggestedByStoreId: null },
  { id: 'mf-4', name: 'NGK', approved: true, suggestedByStoreId: null },
  { id: 'mf-5', name: 'Mann-Filter', approved: true, suggestedByStoreId: null },
  { id: 'mf-6', name: 'Frenosa', approved: false, suggestedByStoreId: 'store-2' },
  { id: 'mf-7', name: 'Imal Repuestos', approved: false, suggestedByStoreId: 'store-5' },
]

let seq = 100
const nextId = (prefix: string) => `${prefix}-${(seq += 1)}`
const clone = <T>(value: T): T => structuredClone(value)

const conflict = (message: string) => new ApiError(message, { status: 409, code: 'CONFLICT' })
const notFound = (what: string) => new ApiError(`No encontramos ${what}.`, { status: 404, code: 'NOT_FOUND' })

function find<T extends { id: string }>(list: T[], id: string, what: string): T {
  const item = list.find((candidate) => candidate.id === id)
  if (!item) throw notFound(what)
  return item
}

function checkPlan(input: PlanInput, ignoreId?: string) {
  if (PLANS.some((plan) => plan.id !== ignoreId && plan.code.toUpperCase() === input.code.toUpperCase())) {
    throw conflict(`Ya existe un plan con el código ${input.code}.`)
  }
}

export function createMockAdminOpsService(): AdminOpsService {
  return {
    simulated: true,

    async plans() {
      await simulateLatency(350)
      return clone(PLANS)
    },
    async createPlan(input) {
      await simulateLatency()
      checkPlan(input)
      const plan = { ...clone(input), id: nextId('plan') }
      PLANS.push(plan)
      return clone(plan)
    },
    async updatePlan(id, input) {
      await simulateLatency()
      const plan = find(PLANS, id, 'el plan')
      checkPlan(input, id)
      Object.assign(plan, clone(input))
      return clone(plan)
    },
    async disablePlan(id) {
      await simulateLatency()
      find(PLANS, id, 'el plan').enabled = false
    },

    async categories() {
      await simulateLatency(300)
      return clone(CATEGORIES)
    },
    async createCategory(code) {
      await simulateLatency()
      if (CATEGORIES.some((category) => category.code.toUpperCase() === code.toUpperCase())) throw conflict(`Ya existe la categoría ${code}.`)
      const category = { id: nextId('cat'), code }
      CATEGORIES.push(category)
      return clone(category)
    },
    async updateCategory(id, code) {
      await simulateLatency()
      const category = find(CATEGORIES, id, 'la categoría')
      if (CATEGORIES.some((other) => other.id !== id && other.code.toUpperCase() === code.toUpperCase())) throw conflict(`Ya existe la categoría ${code}.`)
      category.code = code
      return clone(category)
    },
    async deactivateCategory(id) {
      await simulateLatency()
      const index = CATEGORIES.findIndex((category) => category.id === id)
      if (index < 0) throw notFound('la categoría')
      CATEGORIES.splice(index, 1)
    },

    async manufacturers() {
      await simulateLatency(300)
      return clone(MANUFACTURERS)
    },
    async createManufacturer(name) {
      await simulateLatency()
      if (MANUFACTURERS.some((item) => item.name.toLowerCase() === name.toLowerCase())) throw conflict(`Ya existe el fabricante ${name}.`)
      const manufacturer = { id: nextId('mf'), name, approved: true, suggestedByStoreId: null }
      MANUFACTURERS.push(manufacturer)
      return clone(manufacturer)
    },
    async updateManufacturer(id, change) {
      await simulateLatency()
      const manufacturer = find(MANUFACTURERS, id, 'el fabricante')
      if (change.name !== undefined) {
        const name = change.name
        if (MANUFACTURERS.some((other) => other.id !== id && other.name.toLowerCase() === name.toLowerCase())) throw conflict(`Ya existe el fabricante ${name}.`)
        manufacturer.name = name
      }
      if (change.approved) manufacturer.approved = true
      return clone(manufacturer)
    },
    async deactivateManufacturer(id) {
      await simulateLatency()
      const index = MANUFACTURERS.findIndex((item) => item.id === id)
      if (index < 0) throw notFound('el fabricante')
      MANUFACTURERS.splice(index, 1)
    },
  }
}
