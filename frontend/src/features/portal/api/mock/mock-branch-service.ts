import { ApiError } from '@/lib/api'
import type { BranchService } from '../branch-service'
import type { Branch } from '../types'
import { getStore, newId, requireActiveSubscription, requireCapacity, requirePermission, simulateNetwork } from './mock-state'

function notFound(): ApiError {
  return new ApiError('No encontramos esa sucursal en tu almacén.', { status: 404, code: 'BRANCH_NOT_FOUND' })
}

/** Sucursales simuladas. Crear aplica el cupo `BRANCHES` y la vigencia, como el servidor. */
export function createMockBranchService(): BranchService {
  return {
    async list(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'BRANCH_READ')
      return structuredClone(store.branches)
    },

    async create(storeId, input) {
      await simulateNetwork(700)
      const store = getStore(storeId)
      requirePermission(store, 'BRANCH_CREATE')
      requireActiveSubscription(store)
      requireCapacity(store, 'BRANCHES')
      const branch: Branch = {
        id: newId('branch'),
        storeId,
        name: input.name,
        numberPhone: input.numberPhone,
        state: 'ACTIVE',
        location: {
          addressLine: input.location.addressLine,
          addressComplement: input.location.addressComplement ?? null,
          city: input.location.city,
          department: input.location.department,
          country: input.location.country ?? 'Colombia',
          postalCode: input.location.postalCode ?? null,
          latitude: null,
          longitude: null,
        },
      }
      store.branches.push(branch)
      return structuredClone(branch)
    },

    async update(storeId, branchId, input) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'BRANCH_UPDATE')
      const branch = store.branches.find((candidate) => candidate.id === branchId)
      if (!branch) throw notFound()
      if (input.name !== undefined) branch.name = input.name
      if (input.numberPhone !== undefined) branch.numberPhone = input.numberPhone
      if (input.location && branch.location) {
        branch.location = {
          ...branch.location,
          addressLine: input.location.addressLine,
          addressComplement: input.location.addressComplement ?? null,
          city: input.location.city,
          department: input.location.department,
          postalCode: input.location.postalCode ?? null,
        }
      }
      for (const row of store.products.filter((product) => product.branchId === branchId)) row.branchName = branch.name
      return structuredClone(branch)
    },

    async deactivate(storeId, branchId) {
      await simulateNetwork(500)
      const store = getStore(storeId)
      requirePermission(store, 'BRANCH_DELETE')
      const branch = store.branches.find((candidate) => candidate.id === branchId)
      if (!branch) throw notFound()
      if (store.branches.filter((candidate) => candidate.state === 'ACTIVE').length <= 1 && branch.state === 'ACTIVE') {
        throw new ApiError('Tu almacén necesita al menos una sucursal activa.', { status: 409, code: 'LAST_ACTIVE_BRANCH' })
      }
      branch.state = 'INACTIVE'
    },
  }
}
