import { apiRequest } from '@/lib/api'
import type { StaffService } from '../staff-service'
import type { Staff, StaffInvitation, StaffInvitationAccepted, StaffInvitationPreview, StaffRole } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/staff/StaffController` y `StaffInvitationPublicController`. */
export function createHttpStaffService(): StaffService {
  const invitation = (storeId: string, id: string, suffix: string) =>
    storePath(storeId, `/staff/invitations/${encodeURIComponent(id)}${suffix}`)
  const member = (storeId: string, id: string, suffix: string) =>
    storePath(storeId, `/staff/members/${encodeURIComponent(id)}${suffix}`)

  return {
    list: (storeId) => portalRequest<Staff>(storePath(storeId, '/staff')),
    roles: (storeId) => portalRequest<StaffRole[]>(storePath(storeId, '/staff/roles')),
    invite: (storeId, input) =>
      portalRequest<StaffInvitation>(storePath(storeId, '/staff/invitations'), { method: 'POST', body: input }),
    resend: (storeId, invitationId) =>
      portalRequest<StaffInvitation>(invitation(storeId, invitationId, '/resend'), { method: 'POST' }),
    async revoke(storeId, invitationId) {
      await portalRequest(invitation(storeId, invitationId, '/revoke'), { method: 'PATCH' })
    },
    async changeRole(storeId, memberId, roleCode) {
      await portalRequest(member(storeId, memberId, '/role'), { method: 'PATCH', body: { roleCode } })
    },
    async setActive(storeId, memberId, active) {
      await portalRequest(member(storeId, memberId, active ? '/reactivate' : '/deactivate'), { method: 'PATCH' })
    },
    previewInvitation: (token) =>
      apiRequest<StaffInvitationPreview>(`/staff-invitations/preview?token=${encodeURIComponent(token)}`),
    acceptInvitation: (token, account) =>
      apiRequest<StaffInvitationAccepted>('/staff-invitations/accept', { method: 'POST', body: { token, account } }),
  }
}
