# PLAN — Equipo del portal con roles predefinidos

## Diagnóstico
1. No existe la página `/seller/invitation` en el frontend: el enlace del correo no lleva a ningún sitio, nadie puede aceptar.
2. No hay endpoints ni UI para cambiar el rol de un colaborador ni para desactivarlo/reactivarlo.
3. La invitación obliga a inventar un nombre de rol y marcar hasta 22 casillas; cada invitación crea un rol `INVITED_*` suelto.
4. Invitar exige la capacidad `CUSTOM_ROLES` aunque el cupo real lo controla `USERS`.

## Alcance
Entra: roles predefinidos, invitar por rol, aceptar invitación, cambiar rol, desactivar/reactivar, UI corta por áreas.
No entra: roles a medida, asignación de sucursales a colaboradores, cambio de propietario.

## Contratos
- Roles predefinidos (por almacén, filas en `roles` con código fijo, creados al primer uso y con permisos sincronizados desde código en `StaffRoles`):
  `ADMIN` Administrador (todo salvo propiedad), `SALES` Ventas y pedidos, `INVENTORY` Inventario y productos, `VIEWER` Solo lectura. `OWNER` no es asignable.
- `GET /stores/{id}/staff/roles` → `[{code,name,description,permissionCodes[]}]` (COLLABORATOR_READ). Sustituye a `/permission-options`.
- `POST /stores/{id}/staff/invitations` `{email, roleCode}` (COLLABORATOR_MANAGE + cupo USERS).
- `PATCH /stores/{id}/staff/members/{memberId}/role` `{roleCode}`; `PATCH .../deactivate`; `PATCH .../reactivate` (cupo USERS). No se permite sobre el propietario ni sobre uno mismo.
- `StaffResponse.Member` añade `roleCode` (null en roles heredados) y `owner`; `StaffInvitationResponse` añade `roleCode`.
- Público: `GET /staff-invitations/preview?token=`, `POST /staff-invitations/accept` (sin cambios).
- Frontend: `/seller/invitation?token=` (vista previa + datos de cuenta si no existe).

## Tareas
1. **Backend** (Sonnet, agente `backend`): `StaffRoles` + `StaffDirectory.changeRole/setActive` + casos de uso `ChangeStaffRole`, `SetStaffActive`, `ListStaffRoles`; invitar por `roleCode`; quitar exigencia `CUSTOM_ROLES`. Acepta: tests `-Dtest=StaffRolesTest,ManageStaffMemberTest,InviteStaffMemberTest,StaffInvitationsTest,AcceptStaffInvitationTest` en verde.
2. **Frontend** (Sonnet, agente `frontend`): `invite-dialog` con selector de rol y resumen por áreas; `team-page` con acciones cambiar rol/desactivar/reactivar; página `/seller/invitation`. Acepta: `npm run build` y `npm run lint` en verde.
3. **QA** (Sonnet, `qa-seguridad`): flujo invitar → aceptar → cambiar rol → desactivar contra `autopartco-dev`; IDOR de `memberId` entre almacenes.

## Riesgos
- Roles `INVITED_*` existentes siguen funcionando; se muestran como «Personalizado» hasta cambiarles el rol.
- Desactivar quita el acceso en la siguiente petición (los permisos se resuelven en BD por petición).
