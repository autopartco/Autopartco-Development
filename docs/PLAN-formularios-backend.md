# Plan: backend para las 3 limitaciones de formularios

Fecha: 2026-10-17. Origen: `docs/frontend/forms-audit.md` (limitaciones 2, 3 y 4). Secuencia: `database` → `backend` → `frontend` → `qa-seguridad`.
Nota: `PLAN.md` en la raíz es el plan de productos v2; este plan va aparte para no pisarlo.

## 1. Alcance
Entra: dirección en línea en `placeOrder`; solicitud de cotización Empresarial persistida; idempotencia de «Configurar almacén».
No entra: envío de correos al equipo comercial, CRM/Alegra/Siigo, cambios de frontend (los hace la otra cuenta con estos contratos).

## 2. Contratos

### C1. `POST /api/v1/marketplace/orders` (cambia `PlaceOrderRequest`)
- `addressId` pasa a opcional; nuevo `address` opcional (`PersonAddressRequest` existente) y `saveAddress` (boolean, default false).
- Regla: exactamente uno de `addressId` o `address` (400 si ninguno o ambos).
- Con `address`: se valida y se usa como `AddressSnapshot` del pedido; si `saveAddress=true` se inserta en la libreta en la misma transacción que el pedido (rollback conjunto).
- Sin cambios en `clientRequestId` (ya idempotente).

### C2. `POST /api/v1/stores/{storeId}/quote-requests`
- Auth: sesión opcional; si no hay, `contactEmail` obligatorio.
- Body: `{ contactName, contactEmail, contactPhone?, companyName?, branches?:int, message?:string(<=1000), clientRequestId:UUID }`.
- 201 `{ id, status:"RECEIVED" }`; repetir `clientRequestId` devuelve el mismo `id` (200).
- Tabla `public.store_quote_requests` (id, store_id FK, person_id FK null, contact_*, message, status, client_request_id UNIQUE por store, created_at timestamptz). Rate limit por IP como en el registro público.
- Datos personales: Ley 1581 (finalidad y retención en el texto de consentimiento; `privacy_consents` existente).

### C3. `POST /api/v1/me/store-applications/{applicationId}/setup` (idempotente)
- Si la solicitud ya está configurada: 200 con el mismo `StoreSetupResponse` (sin reprocesar archivos ni duplicar eventos).
- Si está en otro estado inválido: 409 con código estable.

## 3. Decisiones
- Dirección en línea dentro de `placeOrder` en vez de endpoint de dirección temporal: una sola transacción, sin estado huérfano. Descartado: borrar la dirección al abandonar (frágil).
- Cotización como tabla propia, no `mailto:` ni correo directo: auditable y reintentable. Descartado: reutilizar `store_applications` (otro ciclo de vida).
- Idempotencia en el servidor por estado, no por cabecera `Idempotency-Key`: el estado "configurada" ya existe.

## 4. Tareas

### T1. Migración `store_quote_requests`
Modelo: Haiku. Agente: `database`. Toca: `database/migrations/V<ts>__store_quote_requests.sql` (+ `U` de rollback).
Contexto: patrones de `V202610141400__seller_subscription_events_and_payments.sql`; convenciones de `V202610140910`. No ejecutarla (la aplica el usuario).
Aceptación: idempotente (`IF NOT EXISTS`), UNIQUE `(store_id, client_request_id)`, índices por `store_id, created_at`, FK con ON DELETE definido, `timestamptz`.

### T2. Checkout con dirección en línea (C1)
Modelo: Sonnet. Agente: `backend`. Toca: `marketplace/checkout/PlaceOrderRequest.java`, `PlaceMarketplaceOrder.java`, `core/identity/` (método para crear dirección dentro de la transacción), pruebas.
Contexto: `PlaceMarketplaceOrder.java:96` resuelve la dirección con `personAddressLookup.findOwned`.
Aceptación: pruebas `-Dtest=PlaceMarketplaceOrderTest` — ni/ambos → 400; `address`+`saveAddress=true` guarda la dirección; pedido fallido no deja dirección; `addressId` ajeno sigue dando 404/403 actual.

### T3. Cotización Empresarial (C2)
Modelo: Sonnet. Agente: `backend` (después de T1). Toca: paquete nuevo bajo `storesuite/` (entidad, repositorio, caso de uso, controlador, DTO) y pruebas; ruta pública en la configuración de seguridad.
Aceptación: 201 y repetición con mismo `clientRequestId` devuelve mismo `id`; email inválido → 400; rate limit; sin sesión exige `contactEmail`; pruebas con `-Dtest=`.

### T4. Idempotencia de setup (C3)
Modelo: Sonnet. Agente: `backend`. Toca: `storesuite/onboarding/` (`CompleteStoreSetup`, controlador) y pruebas.
Contexto: `StoreApplicationController.completeSetupAuthenticated`. Primero leer cómo marca el estado "configurada".
Aceptación: segunda llamada → 200 mismo cuerpo, sin duplicar archivos ni eventos; estado inválido → 409; pruebas con `-Dtest=`.

### T5. Contratos en docs
Modelo: Haiku (o `redactor`/OpenCode). Toca: `docs/frontend/forms-audit.md` (marcar propuestas como implementadas con el contrato final).
Aceptación: coincide con T2–T4.

### T6. Revisión de seguridad
Modelo: Opus. Agente: `qa-seguridad` (último). Revisar: IDOR en `address`/`addressId`, abuso del endpoint público (spam, enumeración de store), validación de tamaños, PII en logs.
Aceptación: informe de hallazgos; sin críticos abiertos.

## 5. Riesgos
- `BackendApplicationTests` toca la BD del `.env`: usar solo `-Dtest=<Clase>`.
- T3 depende de que la migración T1 esté aplicada por el usuario antes de probar contra BD real.
- Cambiar `addressId` a opcional rompe a clientes viejos solo si exigen el campo; el frontend nuevo es el único cliente.
- Verificar en T4 si ya hay estado "configurada" reutilizable; si no, requeriría una migración adicional (señalar antes de implementar).
