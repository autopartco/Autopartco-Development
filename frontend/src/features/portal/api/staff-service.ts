import { DATA_SOURCE } from '@/lib/api'
import type { PermissionOption, Staff, StaffInvitation, StaffInviteInput } from './types'

export interface StaffService {
  /** `GET /stores/{id}/staff`: colaboradores e invitaciones pendientes. */
  list(storeId: string): Promise<Staff>
  /** `GET /stores/{id}/staff/permission-options` (requiere `COLLABORATOR_MANAGE`). */
  permissionOptions(storeId: string): Promise<PermissionOption[]>
  /** `POST /stores/{id}/staff/invitations` (cupo `USERS` y capability `CUSTOM_ROLES`). */
  invite(storeId: string, input: StaffInviteInput): Promise<StaffInvitation>
  /** `POST /stores/{id}/staff/invitations/{id}/resend`. */
  resend(storeId: string, invitationId: string): Promise<StaffInvitation>
  /** `PATCH /stores/{id}/staff/invitations/{id}/revoke`. */
  revoke(storeId: string, invitationId: string): Promise<void>
}

let servicePromise: Promise<StaffService> | undefined

export function getStaffService(): Promise<StaffService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-staff-service').then((module) => module.createHttpStaffService())
      : import('./mock/mock-staff-service').then((module) => module.createMockStaffService())
  return servicePromise
}
