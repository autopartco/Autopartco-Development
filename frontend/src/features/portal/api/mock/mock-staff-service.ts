import { ApiError } from '@/lib/api'
import type { StaffService } from '../staff-service'
import type { StaffInvitation, StaffRole } from '../types'
import { getStore, isoFromNow, newId, requireActiveSubscription, requireCapacity, requirePermission, simulateNetwork } from './mock-state'

/** Copia de `StaffRoles.DEFINITIONS` del backend. */
export const MOCK_STAFF_ROLES: StaffRole[] = [
  {
    code: 'ADMIN',
    name: 'Administrador',
    description: 'Gestiona todo el almacén y su equipo. No puede cambiar el plan ni la propiedad.',
    permissionCodes: [
      'ALERT_MANAGE', 'ANALYTICS_READ', 'BRANCH_CREATE', 'BRANCH_DELETE', 'BRANCH_READ', 'BRANCH_UPDATE', 'COLLABORATOR_MANAGE',
      'COLLABORATOR_READ', 'INVENTORY_ADJUST', 'INVENTORY_TRANSFER', 'ORDER_READ', 'ORDER_UPDATE', 'PRODUCT_CREATE', 'PRODUCT_DELETE',
      'PRODUCT_IMAGE_MANAGE', 'PRODUCT_PUBLISH', 'PRODUCT_READ', 'PRODUCT_UPDATE', 'PROMOTION_CREATE', 'PROMOTION_DELETE',
      'PROMOTION_READ', 'PROMOTION_UPDATE', 'SHIPMENT_UPDATE', 'STORE_READ', 'STORE_UPDATE',
    ],
  },
  {
    code: 'SALES',
    name: 'Ventas y pedidos',
    description: 'Atiende pedidos y envíos; consulta productos, existencias y promociones.',
    permissionCodes: ['BRANCH_READ', 'ORDER_READ', 'ORDER_UPDATE', 'PRODUCT_READ', 'PROMOTION_READ', 'SHIPMENT_UPDATE', 'STORE_READ'],
  },
  {
    code: 'INVENTORY',
    name: 'Inventario y productos',
    description: 'Crea y publica productos, ajusta existencias y transfiere entre sucursales.',
    permissionCodes: [
      'BRANCH_READ', 'INVENTORY_ADJUST', 'INVENTORY_TRANSFER', 'PRODUCT_CREATE', 'PRODUCT_DELETE', 'PRODUCT_IMAGE_MANAGE',
      'PRODUCT_PUBLISH', 'PRODUCT_READ', 'PRODUCT_UPDATE', 'STORE_READ',
    ],
  },
  {
    code: 'VIEWER',
    name: 'Solo lectura',
    description: 'Consulta el almacén, pedidos, productos y reportes sin poder cambiar nada.',
    permissionCodes: ['ANALYTICS_READ', 'BRANCH_READ', 'COLLABORATOR_READ', 'ORDER_READ', 'PRODUCT_READ', 'PROMOTION_READ', 'STORE_READ'],
  },
]

function notFound(): ApiError {
  return new ApiError('No se encontró una invitación válida.', { status: 404, code: 'SELLER_COLLABORATOR_INVITATION_NOT_FOUND' })
}

function requireRole(code: string): StaffRole {
  const role = MOCK_STAFF_ROLES.find((candidate) => candidate.code === code)
  if (!role) throw new ApiError('Elige uno de los roles disponibles.', { status: 400, code: 'SELLER_COLLABORATOR_ROLE_INVALID' })
  return role
}

/** Equipo simulado: roles predefinidos, cupo `USERS` y suscripción vigente. */
export function createMockStaffService(): StaffService {
  return {
    async list(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_READ')
      return { storeId, members: structuredClone(store.members), invitations: structuredClone(store.invitations) }
    },

    async roles(storeId) {
      await simulateNetwork(250)
      requirePermission(getStore(storeId), 'COLLABORATOR_READ')
      return structuredClone(MOCK_STAFF_ROLES)
    },

    async invite(storeId, input) {
      await simulateNetwork(700)
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_MANAGE')
      requireActiveSubscription(store)
      const role = requireRole(input.roleCode)
      requireCapacity(store, 'USERS')
      const email = input.email.trim().toLowerCase()
      if (store.members.some((member) => member.email === email)) {
        throw new ApiError('Esta persona ya es colaboradora del almacén. Si está desactivada, reactívala desde la lista.', {
          status: 409,
          code: 'SELLER_COLLABORATOR_ALREADY_EXISTS',
        })
      }
      if (store.invitations.some((invitation) => invitation.email === email)) {
        throw new ApiError('Ya existe una invitación pendiente para este correo; puedes reenviarla.', {
          status: 409,
          code: 'SELLER_COLLABORATOR_INVITATION_PENDING',
        })
      }
      const invitation: StaffInvitation = {
        id: newId('inv'),
        email,
        roleCode: role.code,
        roleName: role.name,
        permissionCodes: role.permissionCodes,
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

    async changeRole(storeId, memberId, roleCode) {
      await simulateNetwork(500)
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_MANAGE')
      const role = requireRole(roleCode)
      const member = store.members.find((candidate) => candidate.id === memberId)
      if (!member) throw new ApiError('No se encontró el colaborador en este almacén.', { status: 404, code: 'SELLER_COLLABORATOR_NOT_FOUND' })
      if (member.owner) throw new ApiError('No se puede cambiar el rol ni el acceso del propietario del almacén.', { status: 409, code: 'SELLER_COLLABORATOR_OWNER' })
      member.roleCode = role.code
      member.roleNames = [role.name]
      member.permissionCodes = role.permissionCodes
    },

    async setActive(storeId, memberId, active) {
      await simulateNetwork(500)
      const store = getStore(storeId)
      requirePermission(store, 'COLLABORATOR_MANAGE')
      const member = store.members.find((candidate) => candidate.id === memberId)
      if (!member) throw new ApiError('No se encontró el colaborador en este almacén.', { status: 404, code: 'SELLER_COLLABORATOR_NOT_FOUND' })
      if (member.owner) throw new ApiError('No se puede cambiar el rol ni el acceso del propietario del almacén.', { status: 409, code: 'SELLER_COLLABORATOR_OWNER' })
      if (active && member.status !== 'ACTIVE') requireCapacity(store, 'USERS')
      member.status = active ? 'ACTIVE' : 'INACTIVE'
    },

    async previewInvitation() {
      await simulateNetwork(300)
      throw notFound()
    },

    async acceptInvitation() {
      await simulateNetwork(300)
      throw notFound()
    },
  }
}
