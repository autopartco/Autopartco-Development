# PLAN — Acceso visible al portal para clientes colaboradores

## Objetivo
Un cliente (`customer`) que aceptó una invitación a un almacén ve "Ir a mi almacén" en cabecera, pie, menú de cuenta y `/vender`, igual que un dueño.

Fuera de alcance: varios roles por almacén, cambio del tipo de cuenta, permisos (el servidor ya los valida por almacén).

## Contrato (existente, sin cambios)
`GET /api/v1/me/stores` → `MyStore[]` para cualquier sesión; incluye almacenes donde la cuenta es colaborador activo (`StoreAccess.storesOf`).

## Decisiones
- `useSellerCta` consulta también `/me/stores` para clientes: si hay alguno con `applicationStatus === 'ACTIVE'` → `state: 'manage'`. Descartado: cambiar el tipo de cuenta al aceptar (mezcla rol global con rol por almacén).
- Caché bajo `seller:` con el correo en la clave; se invalida al aceptar la invitación.
- `accountMenuGroups` recibe `hasStore`: el cliente con almacén ve el grupo "Ir al portal del almacén"/"Mis almacenes" + secciones.

## Tareas
1. (Sonnet · frontend) `features/seller/hooks.ts`: `useSellerCta` añade `myStores()`. Criterio: cliente colaborador → `manage`, `/portal`.
2. (Sonnet · frontend) `features/auth/lib/account-menu-items.ts`, `components/account-menu.tsx`: opción `hasStore`. Criterio: menú de cliente colaborador muestra "Ir al portal del almacén".
3. (Haiku · frontend) `features/seller/pages/staff-invitation-page.tsx`: `invalidateAsync('seller:')` y `invalidateAsync('portal:stores')` tras aceptar.
4. (Haiku · qa-seguridad) `npm run build` y `npm run lint` en `frontend/`.

## Riesgos
- Una petición extra por cliente con sesión (cacheada).
