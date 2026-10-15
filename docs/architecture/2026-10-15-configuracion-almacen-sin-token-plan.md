# Plan: terminar la configuración del almacén sin depender del token del correo

## Problema
Al aprobar una solicitud (`CONFIGURATION_PENDING`) se envía `/seller/setup?token=…` con validez de 48 h. `CompleteStoreSetup` es lo único que activa el almacén, inicia la prueba, promueve la cuenta a `STORE_MANAGER` y emite sesión. Si el token vence o se pierde el correo no hay salida: la solicitud queda en `CONFIGURATION_PENDING` y la cuenta sigue siendo `CUSTOMER`, sin acceso al portal.

## Objetivo y alcance
Entra: configurar con la sesión iniciada (sin token), reenviar el enlace, y llevar al usuario a la configuración al iniciar sesión. El correo con token se mantiene.
No entra: cambios de esquema, cambios en la revisión del admin, nuevos pasos de negocio (plan, pago).

## Decisión clave
El portal exige rol `STORE_MANAGER`, que solo existe tras la configuración. Por eso un modal dentro del portal no es posible: se usa la **vista de configuración por pasos** (`/seller/setup`), a la que se redirige al iniciar sesión y se enlaza desde el menú de cuenta y `/portal/solicitud`. Descartado: dar acceso parcial al portal con un rol intermedio (cambia el modelo de permisos).

## Contratos (backend, sin migración)
1. `POST /api/me/store-applications/{applicationId}/setup` — multipart `businessDescription?`, `logo?`, `banner?`. Cuenta autenticada, dueña de la solicitud, en `CONFIGURATION_PENDING`, correo verificado. Misma respuesta y efectos que `POST /store-applications/setup` (`StoreSetupResponse` con tokens nuevos, porque cambia el rol). Errores: `SELLER_APPLICATION_NOT_FOUND` (404, también si no es suya), `SELLER_SETUP_NOT_PENDING` (409), `SELLER_EMAIL_NOT_VERIFIED` (409).
2. `POST /api/me/store-applications/{applicationId}/setup-link` — 204. Genera token nuevo (48 h) y envía el correo. Solo dueña y `CONFIGURATION_PENDING`. Enfriamiento de 5 min derivado de `expiresAt - 48 h`; si no se cumple, 429 `SELLER_SETUP_LINK_COOLDOWN`.
3. `POST /api/store-applications/setup/resend` — body `{ "token": "…" }`, público, siempre 204 (no revela si el token existe). Si corresponde a una solicitud `CONFIGURATION_PENDING` (vencido o no) aplica lo de (2).
Regla común: el token anterior queda invalidado al emitir uno nuevo; `CompleteStoreSetup` extrae un método interno compartido por el flujo con token y el flujo con sesión.

## Contratos (frontend)
- `SellerApplicationService.completeSetupAuthenticated(applicationId, input)`, `requestSetupLink(applicationId)`, `requestSetupLinkByToken(token)` (mock y http).
- `/seller/setup` con y sin `?token=`. Sin token exige sesión (si no, `/ingresar?volver=/seller/setup`). Pasos: Marca (logo, banner) → Descripción → Revisar y activar. Cada paso es opcional salvo el último; el avance se conserva al volver atrás.
- Token vencido/ya usado: pantalla con «Continuar con mi cuenta» (si hay sesión o tras iniciar) y «Enviarme un nuevo enlace».
- Tras iniciar sesión, un cliente con solicitud `CONFIGURATION_PENDING` va a `/seller/setup`. Menú de cuenta y `/portal/solicitud` muestran «Terminar configuración» (`useSellerCta` ya expone `application.status`).
- Al terminar: reemplazar la sesión con los tokens devueltos y navegar a `/portal`.

## Tareas (secuencia backend → frontend → qa-seguridad)

### 1. Setup autenticado y reenvío — backend — Sonnet
Agente `backend`. Archivos: `storesuite/onboarding/CompleteStoreSetup.java`, `StoreApplicationController.java`, nuevo `RequestSetupLink.java`, `core/organization/StoreApplications.java` (`lockForOwnerSetup`, `reissueOnboardingToken`), `EmailService` (reutiliza `sendSellerOnboarding`). Contexto: contratos de arriba; el flujo con token no cambia.
Aceptación: pruebas `-Dtest=` nuevas: setup con sesión activa el almacén y promueve a `STORE_MANAGER`; otra cuenta recibe 404; estado distinto de `CONFIGURATION_PENDING` recibe 409; token anterior queda inválido tras reenviar; enfriamiento devuelve 429; el endpoint público siempre responde 204. `WebSecurityConfig`: solo el `resend` es público.

### 2. Vista de configuración por pasos y servicios — frontend — Sonnet
Agente `frontend`, después de la tarea 1. Archivos: `features/seller/pages/seller-setup-page.tsx`, `features/seller/api/*` (types, http, mock), `features/seller/hooks.ts`. Aceptación: stepper accesible con teclado, flujo con token y con sesión en mock, estado de enlace vencido con las dos salidas, `tsc -b` y lint sin avisos nuevos, probado en navegador en modo mock.

### 3. Redirección y puntos de entrada — frontend — Haiku/Sonnet
Agente `frontend`. Archivos: `features/auth/pages/login-page.tsx`, `features/auth/components/account-menu.tsx`, `account-menu-items.ts`, `features/portal/pages/application-page.tsx`, `portal-index-page.tsx`. Aceptación: cliente con solicitud pendiente de configuración es llevado a `/seller/setup` al iniciar sesión; el menú y `/portal/solicitud` ofrecen «Terminar configuración»; clientes sin solicitud o ya activos no cambian.

### 4. Revisión — qa-seguridad — Opus
Agente `qa-seguridad`, solo lectura. Revisar IDOR del `applicationId`, que el token viejo no sirva, la enumeración en el `resend` público, la fuga de tokens en logs/URL y la subida de archivos en el flujo con sesión. Aceptación: informe de hallazgos con severidad.

## Riesgos y puntos a verificar
- Al promover el rol hay que reemplazar la sesión del frontend con los tokens nuevos, no solo navegar.
- El enfriamiento sin columna depende de que `expiresAt` siga siendo `emisión + 48 h`; si se cambia el TTL hay que ajustarlo.
- Reenvío masivo: limitar también por IP en el endpoint público si no existe un limitador general.
