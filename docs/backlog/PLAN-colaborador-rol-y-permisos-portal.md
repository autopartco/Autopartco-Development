# PLAN — Rol de cuenta del colaborador y permisos del menú del portal

## Diagnóstico
1. Producción (`v2.5.0`) sale de `develop@dc42bc9`: el PR #151 aún no está desplegado.
2. Un cliente existente que acepta una invitación sigue con tipo `CUSTOMER`; una cuenta creada por la invitación queda `STORE_MANAGER`. Inconsistente.
3. Menú del portal: «Publicidad» pide `STORE_READ` pero el backend exige `STORE_UPDATE` o `ANALYTICS_READ`.
4. Sin guarda por sección: con un enlace directo a una sección sin permiso la página falla con 403 y el texto «No tienes acceso a este almacén».
5. Al cambiar de almacén con el selector se conserva la sección aunque el rol del otro almacén no la tenga.

## Decisiones
- Al aceptar, una cuenta `CUSTOMER` pasa a `STORE_MANAGER` (el token sigue con `ROLE_CUSTOMER`: puede comprar). ADMIN/ROOT no cambian. Descartado: dejarla `CUSTOMER` y depender solo de `/me/stores`.
- Sesión iniciada al aceptar: se renueva la sesión (`replaceSessionAfterSetup`) para recibir el rol nuevo.
- Secciones con `permission: StorePermission | StorePermission[]` (basta uno).
- `PortalSectionGuard`: si la ruta pide un permiso que el rol no tiene, redirige a la primera sección permitida.
- 403 dentro del portal: «No tienes permiso para ver esto».

## Tareas
1. (Sonnet · backend) `AcceptStaffInvitation`: promover `CUSTOMER` → `STORE_MANAGER`. Test en `AcceptStaffInvitationTest`.
2. (Sonnet · frontend) `lib/sections.ts`, `portal-nav`, paleta, menú de cuenta: permiso múltiple; publicidad `['STORE_UPDATE','ANALYTICS_READ']`.
3. (Sonnet · frontend) Guarda de sección en `portal-layout.tsx`.
4. (Haiku · frontend) `staff-invitation-page.tsx`: renovar sesión tras aceptar si hay sesión.
5. (Haiku · qa-seguridad) build, lint, `-Dtest=AcceptStaffInvitationTest`, mock en navegador.

## Riesgos
- Un colaborador retirado de todos sus almacenes queda `STORE_MANAGER` y ve el portal vacío (con «Quiero vender»).
