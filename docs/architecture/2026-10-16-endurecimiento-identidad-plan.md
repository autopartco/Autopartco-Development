# Plan: endurecimiento de identidad (auditoría qa-seguridad)

Base: `docs/adr/0003-operaciones-sensibles-exigen-identidad.md`. Reemplaza el flujo con token de `2026-10-15-configuracion-almacen-sin-token-plan.md`.

## Objetivo y alcance
Entra: cerrar H1-H15. No entra: nuevos pasos de negocio, cambios de roles, Flyway (ADR 0001).
Migraciones de BD: **ninguna prevista**. Verificar en T6 que los tokens ya guardan hash y `used_at`/estado; si faltara una columna, `database` crea migración en `database/migrations/` y se avisa antes (requiere aprobación). Los pendientes se invalidan por UPDATE de datos, sin esquema.

## Contratos
- Eliminados: `POST /api/store-applications/setup`, `POST /api/store-applications/setup/resend`.
- Setup (ya existe): `POST /api/me/store-applications/{id}/setup` (multipart; devuelve tokens nuevos por cambio de rol), `POST /api/me/store-applications/{id}/setup-link` (204).
- Nuevos (POST, públicos con rate limit, 204 o error uniforme): `POST /api/auth/verify-email {token}`, `POST /api/auth/unlock {token}`. Los `GET` equivalentes se eliminan (o devuelven 405). Respuesta sin sesión ni tokens.
- Invitación: `GET /api/staff-invitations/{token}` (preview) y `POST /api/staff-invitations/{token}/accept`; con cuenta existente exige sesión cuyo correo == invitado (403 `FORBIDDEN` genérico si no); cuenta nueva envía `{password, ...}`. Todo fallo de token (inexistente, vencido, usado) = mismo `404 STAFF_INVITATION_INVALID`.
- Rate limit: 429 `RATE_LIMITED` con `Retry-After`, por IP y por correo (clave normalizada).
- Frontend rutas: `/seller/setup`, `/seller/invitation?token=`, `/verificar-correo?token=`, `/desbloquear?token=`. El token se lee y se borra de la URL (`history.replaceState`) antes de actuar. Retorno: `/ingresar?volver=<ruta>` validado (relativa, un solo `/` inicial, prefijos permitidos).
- Enlaces de correo apuntan al frontend (`APP_FRONTEND_URL`), nunca a la API.

## Tandas por PR (rama desde `develop`)
- **PR1** H1 + frontend setup (T1-T3). Primero. No desplegar a producción nada de `develop` sin PR1: H1 es explotable hoy.
- **PR2** H2, H3, H8 (T4-T5). Independiente de PR1 salvo en la página de invitación (T10, PR3). Desplegar con PR1.
- **PR3** H4, H5, H6, H14, H15 (T6-T10). Los cambios backend de H5 y los frontend deben ir en el mismo despliegue: si el backend pasa a POST antes de que existan las páginas, los enlaces de correo vigentes fallan. Orden: backend (acepta POST y mantiene GET sin efectos) → frontend → retirar GET.
- **PR4** H7, H9-H13 (T11-T14). Sin dependencias duras; H10 y H11 bloquean el despliegue de pagos/producción (ver Riesgos).
- Dentro de cada PR: backend → frontend → qa-seguridad.

## Tareas

### T1 (PR1, H1 backend) — Sonnet — agente `backend`
Archivos: `storesuite/onboarding/StoreApplicationController.java`, `CompleteStoreSetup.java`, `RequestSetupLink.java`, `OptionalSession.java` (borrar), `shared/security/WebSecurityConfig.java`, `shared/error/GlobalExceptionHandler.java`, `shared/error/ForbiddenException.java`, `core/organization/StoreApplications.java`, tests `WebSecurityConfigTest`, `RequestSetupLinkTest`, `SetupTokenFlowSessionTest`.
Contexto: eliminar `POST /store-applications/setup` y `/setup/resend`, `OptionalSession`, el `BearerTokenResolver` especial y sus rutas públicas; quedan solo los endpoints `/me/...`. Emitir sesión solo en el flujo autenticado. El correo de aprobación enlaza a `/seller/setup` sin token. Conservar del trabajo sin commitear: las mejoras de `RequestSetupLink` y `StoreApplications` (reemisión que invalida el token anterior) y `ForbiddenException`/handler solo si algún endpoint autenticado los usa; descartar lo específico del flujo con token (aviso 403 de cuenta incorrecta ligado al token, `OptionalSession`, `SetupTokenFlowSessionTest`, ajustes de `WebSecurityConfig` para el resolver).
Aceptación (`-Dtest=` por clase): los dos endpoints públicos responden 404/401; setup autenticado activa y promueve; otra cuenta 404; el JWT nunca se emite sin sesión; `WebSecurityConfigTest` verifica que `/store-applications/**` sigue limitado a lo público restante; un token en el enlace no cambia nada.

### T2 (PR1, H1 frontend) — Sonnet — agente `frontend`
Archivos: `frontend/src/features/seller/pages/seller-setup-page.tsx`, `features/seller/api/http/http-seller-service.ts`, `api/mock/mock-seller-service.ts`, types y hooks de `features/seller`, rutas (quitar `PublicRoute`).
Contexto: solo flujo con sesión. Sin sesión → `/ingresar?volver=/seller/setup`. Si la sesión es de otra cuenta que la solicitante (sin solicitud pendiente propia) → aviso con botón «Cambiar de cuenta». Quitar token en memoria, `completeSetup` por token y `requestSetupLinkByToken`. Conservar: stepper por pasos y la redirección tras login (commit 7a439d8). Descartar: lo añadido en el working tree para el flujo con token y el aviso 403 de PR #94 basado en token.
Aceptación: `tsc -b` y lint sin avisos nuevos; en modo mock, sin sesión redirige y vuelve; con `?token=` la página ignora el token; al terminar reemplaza la sesión con los tokens devueltos.

### T3 (PR1) — Opus — agente `qa-seguridad`
Revisión de T1/T2: nada emite sesión sin autenticación previa; IDOR de `applicationId`; el parámetro `volver` rechaza `//evil`, `https://...` y `javascript:`. Aceptación: informe con severidades.

### T4 (PR2, H2/H3/H8 backend) — Sonnet — agente `backend`
Archivos: `AcceptStaffInvitation`, `EmailVerificationService` (rutas en `core`/`storesuite` según código), controlador de invitaciones, tests nuevos.
Contexto: aceptar no reactiva `INACTIVE` ni activa `PENDING` (responde error uniforme). Cuenta existente exige sesión de esa cuenta (H8). `EmailVerificationService` solo transiciona `PENDING→ACTIVE` y `LOCKED→ACTIVE`; cualquier otro estado, sin efecto y mismo resultado externo (H3). Cuenta nueva: contraseña en el mismo acto, token consumido de forma atómica (`UPDATE ... WHERE used_at IS NULL` o lock pesimista).
Aceptación: pruebas de pre-secuestro (correo `PENDING` + invitación no activa la cuenta), `INACTIVE` permanece, doble aceptación concurrente consume una vez, aceptar sin sesión con cuenta existente es 401/403.

### T5 (PR2) — Haiku — agente `backend`
Tests de regresión de H3 por estado (`ACTIVE`, `INACTIVE`, `SUSPENDED`, `LOCKED`, `PENDING`). Aceptación: matriz de estados verde.

### T6 (PR3, H5/H6 backend) — Sonnet — agente `backend`
Archivos: controlador/servicio de verificación y desbloqueo, repositorio de tokens (`AccountToken`), `EmailService` (URLs a frontend), `WebSecurityConfig`.
Contexto: añadir POST verify-email y unlock; los GET no producen efectos. Al reemitir, invalidar los pendientes del mismo tipo y cuenta. Lock pesimista al consumir. Enlaces del correo a `/verificar-correo?token=` y `/desbloquear?token=`. Comprobar que los tokens están hasheados y con `used_at`; si no, avisar (posible migración).
Aceptación: GET no cambia estado; reemitir invalida el anterior; doble POST concurrente solo uno gana; respuesta sin tokens de sesión.

### T7 (PR3, H4) — Sonnet — agente `backend`
Archivos: nuevo filtro `shared/security/RateLimitFilter` (+ config, tests), límite de multipart público (H4: bajar de 35 MB en rutas públicas).
Contexto: implementación en memoria sin dependencias nuevas (Bucket4j u otra librería **requiere aprobación**). Por IP (respetando `X-Forwarded-For` solo de proxy de confianza) y por correo del body en `/auth/**`, `/store-applications/**`, `/staff-invitations/**`. Mapa con expiración para no crecer sin límite.
Aceptación: N+1 peticiones devuelven 429 con `Retry-After`; correo y IP limitan por separado; multipart público rechaza sobre el nuevo tope.

### T8 (PR3, H5/H14/H15 frontend) — Sonnet — agente `frontend`
Archivos: páginas nuevas `features/auth/pages/verify-email-page.tsx`, `unlock-page.tsx`, `features/seller/pages/seller-invitation-page.tsx`, servicios http/mock, rutas.
Contexto: la página lee el token, lo quita de la URL (H14), muestra botón de confirmar y llama POST. Invitación: preview; cuenta existente sin sesión → `/ingresar?volver=…`; cuenta nueva → formulario de contraseña.
Aceptación: mock cubre éxito, token inválido y vencido; el token no queda en la URL ni en el historial; `tsc -b` y lint limpios.

### T9 (PR3) — Haiku — agente `backend`
Retirar los GET de verify-email/unlock tras desplegar T8. Aceptación: 405/404 en GET.

### T10 (PR3) — Opus — agente `qa-seguridad`
Revisar prefetch (GET sin efectos), carreras, 429, fuga de tokens por Referer/logs. Aceptación: informe.

### T11 (PR4, H7/H9) — Sonnet — agente `backend`
Archivos: servicio de invitaciones (TTL 7 d → 72 h, configurable), preview/accept con error uniforme, registro (mitigación H9 con el limitador de T7; mensaje de registro sin revelar existencia si el negocio lo permite, decisión abierta).
Aceptación: invitación nueva vence ≤ 72 h; preview de token inexistente/vencido/usado idéntico (cuerpo y código).

### T12 (PR4, H10/H11) — Opus — agente `backend` (requiere aprobación: seguridad y configuración de despliegue)
Archivos: controlador de webhook Wompi, `application*.yaml`, validador de arranque.
Contexto: validar campo `event` y frescura de `timestamp` (ventana p. ej. 5 min), firma inválida → 401/403, idempotencia por id de evento. En perfil `prod` fallar al arrancar si faltan `WOMPI_*`, si `SIMULATED_PAYMENTS_ENABLED` no es `false` o si `AUTH_COOKIE_SECURE` no es `true`.
Aceptación: tests de firma inválida, evento desconocido, timestamp viejo y repetido; test de arranque con perfil `prod` y configuración insegura falla.

### T13 (PR4, H12/H13) — Sonnet — agente `backend`
H12: por defecto aceptar el riesgo documentado en ADR (JWT corto, refresh revocable); opción: validar `sid` contra `AccountSession` en cada petición (decisión abierta). H13: en `UpdateStorePromotion` comprobar permiso antes de la capacidad del plan.
Aceptación: usuario sin permiso recibe 403 independientemente del plan.

### T14 (PR4) — Opus — agente `qa-seguridad`
Reauditoría completa H1-H15 con pruebas dirigidas. Aceptación: cada hallazgo marcado cerrado, mitigado o aceptado.

## Riesgos y puntos a verificar
- No desplegar nada sin PR1 (H1 es explotable). No desplegar pagos reales sin T12.
- Correos ya enviados: `?token=` en `/seller/setup` debe ignorarse sin romper; enlaces viejos de verify/unlock (API GET) dejan de funcionar al retirar T9; pedir reenvío es la salida.
- El rate limit en memoria no escala a varias instancias; aceptable en monolito de una instancia.
- `X-Forwarded-For` falsificable si no hay proxy de confianza.
- Backend de H5 antes que frontend rompe enlaces vigentes: respetar el orden de PR3.

## Decisiones abiertas para el usuario
1. ¿Aprobar dependencia de rate limit (Bucket4j) o limitador propio en memoria?
2. H12: ¿aceptar JWT sin revocación por `sid` o validar sesión en cada petición (coste de una consulta)?
3. H9: ¿mensaje genérico en registro (peor UX) o solo rate limit?
4. TTL de verificación de correo y desbloqueo (propuesta: 24 h y 1 h) y de restablecer contraseña (1 h).
5. Aprobación de cambios de configuración de producción en T12.
