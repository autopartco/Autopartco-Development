import { DATA_SOURCE } from '@/lib/api'
import type { Branch, BranchInput } from './types'

export interface BranchService {
  /** `GET /stores/{id}/branches`. */
  list(storeId: string): Promise<Branch[]>
  /** `POST /stores/{id}/branches` (aplica el cupo `BRANCHES`). */
  create(storeId: string, input: BranchInput): Promise<Branch>
  /** `PATCH /stores/{id}/branches/{branchId}`. */
  update(storeId: string, branchId: string, input: Partial<BranchInput>): Promise<Branch>
  /** `DELETE /stores/{id}/branches/{branchId}`: desactiva la sucursal (no la borra). */
  deactivate(storeId: string, branchId: string): Promise<void>
}

let servicePromise: Promise<BranchService> | undefined

export function getBranchService(): Promise<BranchService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-branch-service').then((module) => module.createHttpBranchService())
      : import('./mock/mock-branch-service').then((module) => module.createMockBranchService())
  return servicePromise
}
