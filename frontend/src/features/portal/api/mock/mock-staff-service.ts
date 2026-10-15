import { ApiError } from '@/lib/api'
import type { StaffService } from '../staff-service'
import type { PermissionOption, StaffInvitation } from '../types'
import { getStore, isoFromNow, newId, requireActiveSubscription, requireCapability, requireCapacity, requirePermission, simulateNetwork } from './mock-state'

const PERMISSION_OPTIONS: PermissionOption[] = [
  { code: 'STORE_READ', description: 'Ver los datos del almacén' },
  { code: 'PRODUCT_READ', description: 'Ver productos' },
  { code: 'PRODUCT_CREATE', description: 'Crear productos' },
  { code: 'PRODUCT_UPDATE', description: 'Editar productos, precios y existencias' },
  { code: 'PRODUCT_PUBLISH', description: 'Publicar y retirar productos' },
  { code: 'ORDER_READ', description: 'Ver pedidos' },
  { code: 'ORDER_UPDATE', description: 'Cambiar el estado de los pedidos' },
  { code: 'SHIPMENT_UPDATE', description: 'Registrar y actualizar envíos' },
  { code: 'BRANCH_READ', description: 'Ver sucursales' },
]

function notFound(): ApiError {
  return new ApiError('No encontramos esa invitación.', { status: 404, code: 'STAFF_INVITATION_NOT_FOUND' })
}

/** Equipo simulado. Invitar exige la capability `CUSTOM_ROLES`, cupo `USERS` y suscripción vigente. */
export function createMockStaffService(): StaffService {
  return {
    async list(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_READ')
      return { storeId, members: structuredClone(store.members), invitations: structuredClone(store.invitations) }
    },

    async permissionOptions(storeId) {
      await simulateNetwork(250)
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_MANAGE')
      return PERMISSION_OPTIONS
    },

    async invite(storeId, input) {
      await simulateNetwork(700)
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_MANAGE')
      requireActiveSubscription(store)
      requireCapability(store, 'CUSTOM_ROLES')
      requireCapacity(store, 'USERS')
      const email = input.email.trim().toLowerCase()
      if (store.members.some((member) => member.email === email) || store.invitations.some((invitation) => invitation.email === email)) {
        throw new ApiError('Esa persona ya es parte del equipo o tiene una invitación pendiente.', {
          status: 409,
          code: 'STAFF_ALREADY_INVITED',
        })
      }
      const invitation: StaffInvitation = {
        id: newId('inv'),
        email,
        roleName: input.roleName,
        permissionCodes: input.permissionCodes,
        expiresAt: isoFromNow(7),
        expired: false,
        createdAt: isoFromNow(0, new Date().getHours()),
      }
      store.invitations.push(invitation)
      return structuredClone(invitation)
    },

    async resend(storeId, invitationId) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_MANAGE')
      requireActiveSubscription(store)
      const invitation = store.invitations.find((candidate) => candidate.id === invitationId)
      if (!invitation) throw notFound()
      invitation.expiresAt = isoFromNow(7)
      invitation.expired = false
      return structuredClone(invitation)
    },

    async revoke(storeId, invitationId) {
      await simulateNetwork(500)
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_MANAGE')
      if (!store.invitations.some((candidate) => candidate.id === invitationId)) throw notFound()
      store.invitations = store.invitations.filter((candidate) => candidate.id !== invitationId)
    },
  }
}
