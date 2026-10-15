import type { StaffService } from '../staff-service'
import type { PermissionOption, Staff, StaffInvitation } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/staff/StaffController`. */
export function createHttpStaffService(): StaffService {
  const invitation = (storeId: string, id: string, suffix: string) =>
    storePath(storeId, `/staff/invitations/${encodeURIComponent(id)}${suffix}`)

  return {
    list: (storeId) => portalRequest<Staff>(storePath(storeId, '/staff')),
    permissionOptions: (storeId) => portalRequest<PermissionOption[]>(storePath(storeId, '/staff/permission-options')),
    invite: (storeId, input) =>
      portalRequest<StaffInvitation>(storePath(storeId, '/staff/invitations'), { method: 'POST', body: input }),
    resend: (storeId, invitationId) =>
      portalRequest<StaffInvitation>(invitation(storeId, invitationId, '/resend'), { method: 'POST' }),
    async revoke(storeId, invitationId) {
      await portalRequest(invitation(storeId, invitationId, '/revoke'), { method: 'PATCH' })
    },
  }
}
