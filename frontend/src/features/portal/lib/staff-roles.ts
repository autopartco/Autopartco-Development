import type { StaffRole, StaffRoleCode } from '../api/types'
import { PERMISSION_LABEL } from './labels'

/** Áreas del portal para resumir un rol sin listar cada permiso. Solo presentación: el servidor decide. */
export const STAFF_AREAS = [
  { id: 'store', label: 'Tienda y sucursales', codes: ['STORE_READ', 'STORE_UPDATE', 'BRANCH_READ', 'BRANCH_CREATE', 'BRANCH_UPDATE', 'BRANCH_DELETE'] },
  {
    id: 'products',
    label: 'Productos e inventario',
    codes: ['PRODUCT_READ', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_PUBLISH', 'PRODUCT_DELETE', 'PRODUCT_IMAGE_MANAGE', 'INVENTORY_ADJUST', 'INVENTORY_TRANSFER'],
  },
  { id: 'orders', label: 'Pedidos y envíos', codes: ['ORDER_READ', 'ORDER_UPDATE', 'SHIPMENT_UPDATE'] },
  { id: 'promotions', label: 'Promociones', codes: ['PROMOTION_READ', 'PROMOTION_CREATE', 'PROMOTION_UPDATE', 'PROMOTION_DELETE'] },
  { id: 'reports', label: 'Reportes y alertas', codes: ['ANALYTICS_READ', 'ALERT_MANAGE'] },
  { id: 'team', label: 'Equipo', codes: ['COLLABORATOR_READ', 'COLLABORATOR_MANAGE'] },
] as const

export type AreaAccess = 'manage' | 'read' | 'none'

export const AREA_ACCESS_LABEL: Record<AreaAccess, string> = {
  manage: 'Gestiona',
  read: 'Solo consulta',
  none: 'Sin acceso',
}

/** Nivel de acceso por área: «Gestiona» si tiene algún permiso que no sea de lectura. */
export function areaAccess(permissionCodes: readonly string[]): { id: string; label: string; access: AreaAccess; codes: string[] }[] {
  const granted = new Set(permissionCodes)
  return STAFF_AREAS.map((area) => {
    const codes = area.codes.filter((code) => granted.has(code))
    const access: AreaAccess = codes.length === 0 ? 'none' : codes.some((code) => !code.endsWith('_READ')) ? 'manage' : 'read'
    return { id: area.id, label: area.label, access, codes }
  })
}

export function permissionLabel(code: string): string {
  return PERMISSION_LABEL[code as keyof typeof PERMISSION_LABEL] ?? EXTRA_LABEL[code] ?? code
}

const EXTRA_LABEL: Record<string, string> = {
  STORE_UPDATE: 'Editar el almacén y la publicidad',
  BRANCH_CREATE: 'Crear sucursales',
  BRANCH_UPDATE: 'Editar sucursales',
  BRANCH_DELETE: 'Desactivar sucursales',
  PRODUCT_DELETE: 'Eliminar productos',
  PRODUCT_IMAGE_MANAGE: 'Gestionar fotos de productos',
  COLLABORATOR_READ: 'Ver el equipo',
  COLLABORATOR_MANAGE: 'Invitar y administrar el equipo',
}

export function roleName(roles: readonly StaffRole[] | undefined, code: StaffRoleCode | null, fallback: string): string {
  if (code === 'OWNER') return 'Propietario'
  return roles?.find((role) => role.code === code)?.name ?? fallback
}
