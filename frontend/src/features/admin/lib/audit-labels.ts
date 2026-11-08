/** Acciones de auditoría en lenguaje claro. Las desconocidas se muestran con su código. */
export const AUDIT_ACTION_LABEL: Record<string, string> = {
  LOGIN_SUCCEEDED: 'Ingreso correcto',
  LOGIN_FAILED: 'Ingreso fallido',
  SOCIAL_LOGIN: 'Ingreso con Google/Facebook',
  SOCIAL_LINKED: 'Cuenta vinculada a Google',
  USER_REGISTERED_SOCIAL: 'Registro con Google/Facebook',
  USER_REGISTRATION_REQUESTED: 'Registro de cuenta',
  USER_EMAIL_VERIFIED: 'Correo verificado',
  ACCOUNT_LOCKED: 'Cuenta bloqueada',
  ACCOUNT_UNLOCKED: 'Cuenta desbloqueada',
  PASSWORD_RESET: 'Contraseña restablecida',
  PLAN_CREATED: 'Plan creado',
  PLAN_UPDATED: 'Plan editado',
  PLAN_DISABLED: 'Plan deshabilitado',
  PLATFORM_PROMOTION_CREATED: 'Promoción creada',
  PLATFORM_PROMOTION_UPDATED: 'Promoción editada',
  PLATFORM_PROMOTION_DELETED: 'Promoción eliminada',
  CATEGORY_CREATED: 'Categoría creada',
  CATEGORY_UPDATED: 'Categoría editada',
  CATEGORY_DEACTIVATED: 'Categoría desactivada',
  MANUFACTURER_CREATED: 'Fabricante creado',
  MANUFACTURER_UPDATED: 'Fabricante editado',
  MANUFACTURER_APPROVED: 'Fabricante aprobado',
  MANUFACTURER_DEACTIVATED: 'Fabricante desactivado',
  STORE_APPLICATION_REVIEWED: 'Solicitud de almacén revisada',
  STAFF_INVITED: 'Invitación de equipo',
  STAFF_INVITATION_REVOKED: 'Invitación revocada',
}

export const AUDIT_ENTITY_LABEL: Record<string, string> = {
  ACCOUNT: 'Cuenta',
  PLAN: 'Plan',
  PROMOTION: 'Promoción',
  CATEGORY: 'Categoría',
  MANUFACTURER: 'Fabricante',
  STORE_APPLICATION: 'Solicitud de almacén',
  STAFF_INVITATION: 'Invitación de equipo',
}

export const auditActionLabel = (code: string) => AUDIT_ACTION_LABEL[code] ?? code
export const auditEntityLabel = (code: string) => AUDIT_ENTITY_LABEL[code] ?? code

/** Acciones que conviene destacar al revisar. */
export const AUDIT_WARNING = new Set(['LOGIN_FAILED', 'ACCOUNT_LOCKED', 'PLAN_DISABLED', 'PLATFORM_PROMOTION_DELETED', 'CATEGORY_DEACTIVATED', 'MANUFACTURER_DEACTIVATED'])
