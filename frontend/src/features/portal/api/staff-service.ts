import { DATA_SOURCE } from '@/lib/api'
import type {
  Staff,
  StaffInvitation,
  StaffInvitationAccepted,
  StaffInvitationAccount,
  StaffInvitationPreview,
  StaffInviteInput,
  StaffRole,
  StaffRoleCode,
} from './types'

export interface StaffService {
  /** `GET /stores/{id}/staff`: colaboradores e invitaciones pendientes. */
  list(storeId: string): Promise<Staff>
  /** `GET /stores/{id}/staff/roles`: roles predefinidos asignables. */
  roles(storeId: string): Promise<StaffRole[]>
  /** `POST /stores/{id}/staff/invitations` (cupo `USERS`). */
  invite(storeId: string, input: StaffInviteInput): Promise<StaffInvitation>
  /** `POST /stores/{id}/staff/invitations/{id}/resend`. */
  resend(storeId: string, invitationId: string): Promise<StaffInvitation>
  /** `PATCH /stores/{id}/staff/invitations/{id}/revoke`. */
  revoke(storeId: string, invitationId: string): Promise<void>
  /** `PATCH /stores/{id}/staff/members/{id}/role`. */
  changeRole(storeId: string, memberId: string, roleCode: StaffRoleCode): Promise<void>
  /** `PATCH /stores/{id}/staff/members/{id}/deactivate|reactivate` (reactivar ocupa cupo `USERS`). */
  setActive(storeId: string, memberId: string, active: boolean): Promise<void>
  /** `GET /staff-invitations/preview?token=` (público). */
  previewInvitation(token: string): Promise<StaffInvitationPreview>
  /** `POST /staff-invitations/accept` (público): `account` solo si el correo no tiene cuenta. */
  acceptInvitation(token: string, account?: StaffInvitationAccount): Promise<StaffInvitationAccepted>
}

let servicePromise: Promise<StaffService> | undefined

export function getStaffService(): Promise<StaffService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-staff-service').then((module) => module.createHttpStaffService())
      : import('./mock/mock-staff-service').then((module) => module.createMockStaffService())
  return servicePromise
}
