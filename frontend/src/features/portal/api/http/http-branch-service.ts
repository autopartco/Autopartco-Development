import type { BranchService } from '../branch-service'
import type { Branch } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/branches/BranchController`. */
export function createHttpBranchService(): BranchService {
  return {
    list: (storeId) => portalRequest<Branch[]>(storePath(storeId, '/branches')),
    create: (storeId, input) => portalRequest<Branch>(storePath(storeId, '/branches'), { method: 'POST', body: input }),
    update: (storeId, branchId, input) =>
      portalRequest<Branch>(storePath(storeId, `/branches/${encodeURIComponent(branchId)}`), {
        method: 'PATCH',
        body: input,
      }),
    async deactivate(storeId, branchId) {
      await portalRequest(storePath(storeId, `/branches/${encodeURIComponent(branchId)}`), { method: 'DELETE' })
    },
  }
}
