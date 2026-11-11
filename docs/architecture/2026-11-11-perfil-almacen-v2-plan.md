# Perfil de almacén v2: servicios, mapa, WhatsApp medido, configuraciones y verificación de correo

Rama: `feature/perfil-almacen` (continúa) o `feature/perfil-almacen-v2` desde `develop`. Secuencia: `database` → `backend` → `frontend` → `qa-seguridad`.

## Objetivo y alcance

Entra:
1. **Servicios** del almacén (instalación, alineación, diagnóstico…) por sucursal, visibles en el perfil.
2. **Ubicación**: mapa de sucursales en el perfil y botón «Cómo llegar» (Google Maps, Waze, Apple Maps).
3. **WhatsApp para cotizar** por sucursal con mensaje prellenado (producto + enlace) y conteo de clics como métrica.
4. **Perfil v2**: banner visible, redes del jefe de almacén, horarios, sucursales, productos por sucursal, destacados, servicios.
5. **Configuraciones** de cuenta y almacén sobre `accounts.configuration` y `store.configuration` (incluye 2FA por correo).
6. **Verificación de correo**: enlace + código de 6 dígitos en el mismo correo (HTML), reenvío y aviso al iniciar sesión sin verificar.

No entra: TOTP/app autenticadora (solo 2FA por correo), reserva/agenda de servicios, pago de servicios, chat interno, API de WhatsApp Business, geocodificación automática (la sucursal fija el punto en el mapa), slug amigable.

## Estado actual (hallazgos)

- `locations` ya tiene `latitude/longitude`; `Branch` tiene `numberPhone` pero no WhatsApp ni horario.
- `accounts.configuration` existe y siempre es `{}`; `store.configuration` solo guarda `description` y `publicProfile`.
- Perfil público ya pinta banner (`StoreBanner`) solo si `bannerUrl` carga; el portal no lo exige ni lo muestra en vista previa grande. Redes: solo WhatsApp, Instagram, Facebook, TikTok, web.
- No hay librería de mapas en el frontend.
- Verificación: solo enlace, correo en texto plano (`SimpleMailMessage`), enviado **dentro** de la transacción de registro (si falla SMTP, falla el registro). No hay reenvío. `LoginService` responde `INVALID_CREDENTIALS` a una cuenta `PENDING` aunque la contraseña sea correcta: el usuario nunca sabe que debe verificar.
- `store_visibility_events` ya existe (T1 del plan 2026-11-08) con `kind in (PROFILE_VIEW, PRODUCT_VIEW, SHARE)`.

## Componentes

| Capa | Componente | Responsabilidad |
|---|---|---|
| BD | `store_services`, `store_service_branches` | Servicios y sucursales que los prestan |
| BD | `branches.whatsapp`, `branches.opening_hours` | Contacto y horario por sucursal |
| BD | `store_visibility_events` (+`branch_id`, kind `WHATSAPP_CLICK`) | Métrica de cotización por WhatsApp |
| BD | `account_tokens.attempts` | Límite de intentos del código corto |
| backend | `core/organization` (sucursales públicas, servicios) | Lectura/escritura de servicios y sucursales |
| backend | `core/settings` (nuevo) | Esquema, validación y valores por defecto de configuraciones |
| backend | `core/identity` | Código corto, reenvío, aviso de no verificado, 2FA por correo |
| backend | `core/notification` | Correos HTML con plantilla común, envío tras commit y asíncrono |
| backend | `core/analytics` | `WHATSAPP_CLICK`, por sucursal y producto |
| frontend | `marketplace/pages/store-page.tsx` + componentes | Perfil v2, mapa, WhatsApp, servicios |
| frontend | `portal` (perfil, sucursales, servicios, configuración, visibilidad) | Edición y métricas |
| frontend | `auth` + `account/settings` | Verificación, reenvío, 2FA, configuración de cuenta |

## Contratos

### Datos (Supabase `autopartco-dev`, no en el repo)

```sql
alter table branches add column whatsapp varchar(13) null check (whatsapp ~ '^\+57\d{10}$');
alter table branches add column opening_hours jsonb null; -- ver formato abajo

create table store_services (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references store(id),
  category varchar(30) not null,            -- ServiceCategory
  name varchar(120) not null,
  description varchar(1000) null,
  price_from numeric(14,2) null check (price_from >= 0),  -- COP, null = «a cotizar»
  duration_minutes int null check (duration_minutes between 5 and 10080),
  active boolean not null default true,
  sort_order int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index ix_store_services_store on store_services (store_id) where active;
create table store_service_branches (
  service_id uuid not null references store_services(id) on delete cascade,
  branch_id uuid not null references branches(id) on delete cascade,
  primary key (service_id, branch_id)
);
alter table store_services enable row level security;
alter table store_service_branches enable row level security;

alter table store_visibility_events add column branch_id uuid null references branches(id);
alter table store_visibility_events drop constraint store_visibility_events_kind_check;
alter table store_visibility_events add constraint store_visibility_events_kind_check
  check (kind in ('PROFILE_VIEW','PRODUCT_VIEW','SHARE','WHATSAPP_CLICK','DIRECTIONS_CLICK'));
create index ix_sve_store_branch_day on store_visibility_events (store_id, branch_id, day) where branch_id is not null;

alter table account_tokens add column attempts smallint not null default 0;
```
(Verificar nombres reales de tablas/constraint con `list_tables` antes de aplicar.)

`opening_hours`: `{ "mon": [["08:00","18:00"]], "tue": [...], …, "sun": [], "notes": "Festivos 9-13" }` — días `mon..sun`, 0–2 franjas `HH:mm` ascendentes, `notes` ≤ 120.

`ServiceCategory`: `MECHANICS, ELECTRICAL, ALIGNMENT_BALANCING, TIRES, LUBRICATION, BODY_PAINT, INSTALLATION, DIAGNOSTICS, AC, OTHER`.

### Configuraciones (`core/settings`)

Claves fijas, validadas en el backend; lo no enviado conserva su valor; las claves desconocidas → 400 `UNKNOWN_SETTING`. Las lecturas siempre devuelven el objeto completo con valores por defecto.

Cuenta (`accounts.configuration`):
```json
{ "security": { "twoFactor": { "enabled": false, "method": "EMAIL" }, "loginAlerts": true },
  "notifications": { "orderUpdates": true, "priceAlerts": true, "storeReplies": true, "marketing": false },
  "preferences": { "theme": "system", "defaultCityCode": null } }
```
Almacén (`store.configuration`, junto a `description` y `publicProfile`, que no cambian de lugar):
```json
{ "contact": { "whatsappEnabled": true, "whatsappMessage": "Hola, vi {producto} en Autopartco y quiero cotizarlo: {enlace}" },
  "profile": { "showBranchesMap": true, "featuredProductIds": [], "tagline": null },
  "catalog": { "showExactStock": false },
  "notifications": { "newOrder": true, "lowStock": true, "newReview": true, "weeklyReport": true, "recipients": [] },
  "security": { "requireStaffTwoFactor": false } }
```
Reglas: `whatsappMessage` ≤ 300, debe contener `{enlace}`; `featuredProductIds` ≤ 8, publicados y del almacén; `tagline` ≤ 80; `recipients` ≤ 5 correos; `marketing` respeta el registro de consentimientos (Ley 1581) si existe; `requireStaffTwoFactor=true` → colaboradores sin 2FA reciben 403 `STAFF_TWO_FACTOR_REQUIRED` en `/stores/{id}/**` (el dueño también).
`publicProfile` añade `youtube` y `x` (`^[A-Za-z0-9._]{1,30}$`) y `email` público.

### API pública

`GET /api/v1/marketplace/stores/{storeId}` (`StorePublicProfile`) añade:
```json
{ "tagline": "…|null", "verified": true,
  "contact": { "…existentes", "youtube": null, "x": null, "email": null },
  "whatsappEnabled": true, "whatsappMessage": "Hola, vi {producto}…",
  "services": [{ "id": "uuid", "category": "ALIGNMENT_BALANCING", "name": "…", "description": "…", "priceFrom": 60000, "durationMinutes": 45, "branchIds": ["uuid"] }],
  "featuredProductIds": ["uuid"] }
```
`GET /api/v1/marketplace/stores/{storeId}/branches` → `[{ "id", "name", "address": "Cra 7 # 10-20", "city", "department", "latitude": 4.6 | null, "longitude": -74.0 | null, "phone": "+57…|null", "whatsapp": "+57…|null", "openingHours": {…}|null, "openNow": true|null, "serviceIds": [uuid] }]` — solo sucursales `ACTIVE`; `whatsapp` cae a `publicProfile.whatsapp` si la sucursal no tiene; `null` si `whatsappEnabled=false`. `openNow` en hora de Bogotá.
`GET /api/v1/marketplace/products?storeId=&branchId=` — `branchId` filtra por inventario publicado de esa sucursal (debe pertenecer a `storeId`).
`GET /api/v1/marketplace/services?category=&city=` (opcional, T4) → tiendas que ofrecen la categoría.

`POST /api/v1/events/visibility` acepta `kind: WHATSAPP_CLICK | DIRECTIONS_CLICK` con `branchId` obligatorio (de ese almacén) y `productId`/`serviceId` opcionales; mismo límite por IP; 204 siempre.

Enlaces (los arma el frontend):
- WhatsApp: `https://wa.me/<digits>?text=<encodeURIComponent(mensaje)>`, con `{producto}` = nombre del producto o servicio (o del almacén si es genérico) y `{enlace}` = URL canónica **sin** `ref`.
- Cómo llegar: con coordenadas `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>`, `https://waze.com/ul?ll=<lat>,<lng>&navigate=yes`, `https://maps.apple.com/?daddr=<lat>,<lng>`; sin coordenadas, `destination=<dirección, ciudad, Colombia>` codificada.

### API del portal

- `GET/PUT /api/v1/stores/{storeId}/services[/{serviceId}]`, `POST …/services`, `DELETE …/services/{id}` (soft: `active=false`). Permiso `STORE_UPDATE`. `branchIds` deben ser del almacén → 400 `BRANCH_NOT_IN_STORE`. Máx. 50 servicios activos.
- `PATCH /api/v1/stores/{storeId}/branches/{branchId}` acepta `whatsapp`, `openingHours`, y `location.latitude/longitude` (rango de Colombia: lat −4.3…13.5, lng −82…−66.8 → si no, 400 `LOCATION_OUT_OF_RANGE`).
- `GET/PATCH /api/v1/stores/{storeId}/settings` → objeto almacén completo. `STORE_UPDATE`.
- `GET /analytics/visibility` añade: `whatsappClicks`, `directionsClicks`; `bySource[].whatsappClicks`; `topProducts[].whatsappClicks`; `byBranch: [{ branchId, name, whatsappClicks, directionsClicks }]`; `daily[].whatsappClicks`. «Clics a publicaciones» = `productViews` (se rotula así).

### API de cuenta e identidad

- `GET/PATCH /api/v1/account/settings` → objeto cuenta completo (sesión).
- Registro: un correo HTML (con alternativa de texto) que contiene botón con enlace `…/verify-email?token=` y código de 6 dígitos (`SecureRandom`, hash SHA-256 con el id de cuenta como sal, purpose `EMAIL_VERIFICATION_CODE`). Ambos expiran en `emailVerification.expiresIn` (código: máx. 30 min). Envío tras commit (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`); un fallo SMTP se registra y no revierte el registro.
- `POST /api/v1/auth/verify-email/code` `{ email, code }` → 200 `VerifyEmailResponse` · 400 `INVALID_VERIFICATION_CODE` (mismo mensaje si el correo no existe) · 429 `VERIFICATION_ATTEMPTS_EXCEEDED` tras 5 intentos (el código queda inválido; hay que reenviar). Al verificar por cualquiera de las dos vías se invalidan el enlace y el código pendientes.
- `POST /api/v1/auth/verify-email/resend` `{ email }` → 202 siempre con `{ "message": "Si la cuenta existe y no está verificada, te enviamos un correo.", "retryAfterSeconds": 60 }`. Reenvía solo si la cuenta está `PENDING`; invalida tokens previos; espera 60 s entre envíos y máx. 5 por hora por cuenta (si no, 202 sin enviar). Límite por IP como el resto de `/auth`.
- Login con contraseña correcta y cuenta `PENDING` → 403 `EMAIL_NOT_VERIFIED` `{ details: { email: "lu***@gmail.com" } }`; con contraseña incorrecta sigue `INVALID_CREDENTIALS` (no revela estado). Login social de correo no verificado sigue su flujo actual.
- 2FA por correo (si `security.twoFactor.enabled`): login correcto → 200 `{ "twoFactorRequired": true, "challengeId": "uuid", "expiresAt": … }` sin tokens; `POST /api/v1/auth/login/two-factor` `{ challengeId, code }` → `LoginResponse`; 5 intentos; reenvío `POST /auth/login/two-factor/resend { challengeId }` con las mismas esperas. Activar 2FA exige confirmar un código enviado al correo (`POST /account/settings/two-factor/confirm`). `loginAlerts` envía correo al iniciar sesión desde un user-agent nuevo.

### Frontend

- Perfil `/tiendas/:storeId` (diseño según `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md` §17, §20.15): cabecera con banner a ancho completo (degradado de marca si falta), logo superpuesto, nombre, tagline, insignia verificada, calificación, «Miembro desde»; barra de acciones: **WhatsApp** (elige sucursal si hay varias), **Cómo llegar**, Compartir, redes. Pestañas: Inicio (destacados, servicios, mapa), Repuestos (selector de sucursal → `branchId`), Servicios, Sucursales (mapa + tarjetas con dirección, horario, «Abierto ahora», WhatsApp, Cómo llegar), Reseñas, Información.
- Mapa: `leaflet` + `react-leaflet` con teselas OpenStreetMap y atribución, carga diferida (`lazy`) solo al ver el mapa; sucursales sin coordenadas aparecen solo en la lista. **Instalar dependencias requiere autorización.**
- Producto: botón «Cotizar por WhatsApp» junto al precio con la sucursal del listado; registra `WHATSAPP_CLICK` (`fetch keepalive`) y abre `wa.me` en pestaña nueva.
- Portal: perfil (banner obligatorio en el checklist de perfil completo, vista previa grande), sucursales (WhatsApp, horario, fijar punto en el mapa), servicios (CRUD), configuración del almacén, visibilidad con WhatsApp y por sucursal.
- Cuenta: `/cuenta/configuracion` (seguridad, notificaciones, preferencias). Auth: pantalla `/verificar-correo` con campo de 6 dígitos, botón «Reenviar» con cuenta regresiva, y redirección allí desde login ante `EMAIL_NOT_VERIFIED`; pantalla de código 2FA.

## Decisiones

- **Servicios en tabla propia, no como producto:** no tienen stock, fabricante ni compatibilidad; reusar `products` rompe reglas de publicación. Descartado: `store.configuration` (no permite filtrar por categoría/ciudad).
- **WhatsApp por sucursal con respaldo al del almacén:** las sucursales atienden distinto; sin columna nueva el dato quedaría mezclado con `numberPhone`.
- **Clic medido por evento, no por redirección del servidor:** un `GET` que redirige lo inflan prefetch y bots; el evento usa el mismo límite y hash diario existentes.
- **Leaflet + OSM:** sin clave ni costo; Google Maps JS exige clave y facturación. Riesgo de política de uso de teselas OSM en volumen alto (ver riesgos).
- **Configuración en jsonb con esquema en código:** las columnas ya existen; el esquema fijo evita basura y da valores por defecto.
- **2FA por correo y no TOTP:** reutiliza `account_tokens` y el correo; TOTP queda para después.
- **Enlace + código en el mismo correo:** el enlace sirve en el mismo dispositivo; el código, cuando se abre el correo en otro.

## Tareas

### T1 — Esquema ✅ (2026-11-11)

Notas de implementación (migración `store_services_whatsapp_settings`): además del SQL de «Datos» se añadió `store_services.version` (bloqueo optimista como el resto), check de `category` y nombre no vacío, `store_visibility_events.service_id` (FK a `store_services`) y check `chk_sve__branch_required` (`WHATSAPP_CLICK`/`DIRECTIONS_CLICK` exigen `branch_id`). `chk_account_tokens__purpose` ahora admite `EMAIL_VERIFICATION_CODE`, `TWO_FACTOR_LOGIN` y `TWO_FACTOR_SETUP` (T2 y T3 los usan). Advisors: solo el INFO «RLS sin políticas», igual que el resto de tablas.
- **Modelo:** Haiku · **Agente:** database
- **Archivos:** ninguno (SQL solo en Supabase `autopartco-dev`).
- **Contexto:** inspeccionar con `list_tables` (nombres reales de `branches`, `account_tokens`, constraint de `store_visibility_events`) y aplicar el SQL de «Datos» con `apply_migration` nombre `store_services_whatsapp_settings`. **Requiere autorización.**
- **Aceptación:** existen las tablas/columnas e índices; insertar `WHATSAPP_CLICK` funciona; `get_advisors` sin alertas nuevas.

### T2 — Verificación de correo, reenvío y aviso en login ✅ (2026-11-11)

Notas de implementación: código incorrecto, vencido, correo inexistente o cuenta ya activa → mismo 400 `INVALID_VERIFICATION_CODE` («El código no es válido o expiró.»). El registro de vendedor también envía tras commit: un fallo SMTP ya no revierte el alta. No hay limitador por IP en `/auth`; la protección es por cuenta (5 intentos; reenvío 60 s / 5 por hora). Executor de correo acotado (1–3 hilos, cola 200, descarta si se llena; el usuario puede reenviar).
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** `core/identity/internal/{EmailVerificationService,EmailVerificationTokenService,RegistrationService,LoginService,AccountToken}.java`, `core/identity/web/AuthController.java`, nuevos `VerifyEmailCodeRequest`, `ResendVerificationRequest`; `core/notification/EmailService.java` (HTML `MimeMessage` + texto, plantilla común), evento tras commit; `WebSecurityConfig` (rutas públicas nuevas, **requiere aprobación**); tests en `backend/src/test/java/com/autopartco/backend/core/identity/`.
- **Contexto:** contratos «API de cuenta e identidad» (sin 2FA). Depende de T1 (`attempts`).
- **Aceptación:** `./mvnw -q test -Dtest=EmailVerificationCodeTest,ResendVerificationTest,LoginUnverifiedTest` en verde: código correcto activa; 5 fallos → 429 y código inválido; reenvío en <60 s no envía; correo inexistente → 202 igual; `PENDING` + contraseña correcta → 403 `EMAIL_NOT_VERIFIED`, incorrecta → `INVALID_CREDENTIALS`; SMTP que falla no revierte el registro.

### T3 — Configuraciones de cuenta/almacén y 2FA por correo ✅ (2026-11-11)

Notas de implementación:
- Errores de settings: 400 `UNKNOWN_SETTING` / `INVALID_SETTING` con `details.key`.
- Puerto para T4: `core.settings.SettingsQueries` (`storeSettings`, `requiresStaffTwoFactor`, `accountSettings`), con caché de 30 s por instancia.
- Activar/desactivar 2FA:
  - `POST /account/settings/two-factor/request {action: ENABLE|DISABLE}` → 202 `{sent, retryAfterSeconds, codeValidMinutes}`.
  - `…/confirm {code, action?}` → 200 `{enabled, method}`.
  - `…/disable {password}`. Desactivar por código solo se permite a cuentas con proveedor social vinculado.
- Login con 2FA: `LoginResponse` añade `twoFactorRequired`, `challengeId` y `expiresAt`; los tokens van nulos y no se emite cookie hasta el código.
  - Errores del código: 400 `INVALID_TWO_FACTOR_CODE` y, al 5.º fallo, 429 `TWO_FACTOR_ATTEMPTS_EXCEEDED`.
  - **`/auth/login/two-factor/resend` devuelve un `challengeId` nuevo**, y el frontend debe usarlo.
- `requireStaffTwoFactor` se aplica con un interceptor MVC (`StaffTwoFactorInterceptor`) sobre `/stores/{id}/**`. Responde 403 `STAFF_TWO_FACTOR_REQUIRED`; `/stores/{id}/settings` queda exenta.
- `notifications.marketing` solo guarda la preferencia, porque no hay servicio de consentimientos.
- Pendiente: desactivar con contraseña no tiene límite de intentos (cada fallo se audita), y `SessionIssuer` (final de la configuración del almacén) no pide 2FA.
- **Modelo:** Opus · **Agente:** backend
- **Archivos:** nuevo `core/settings/{AccountSettings,StoreSettings,SettingsService,SettingsController}.java`; `core/identity` (desafío 2FA, alerta de inicio); `storesuite/store` (`/settings`); filtro de `requireStaffTwoFactor`; `WebSecurityConfig` (**aprobación**); tests en `backend/src/test/java/com/autopartco/backend/core/settings/`.
- **Contexto:** contratos «Configuraciones» y 2FA. Depende de T2 (plantilla de correo y códigos).
- **Aceptación:** `./mvnw -q test -Dtest=SettingsServiceTest,TwoFactorLoginTest,StaffTwoFactorTest` en verde: clave desconocida → 400; defaults en lectura; `whatsappMessage` sin `{enlace}` → 400; login con 2FA no emite tokens hasta el código; `requireStaffTwoFactor` bloquea colaborador sin 2FA.

### T4 — Servicios, sucursales públicas y WhatsApp medido ✅ (2026-11-11)

Notas de implementación:
- **Filtro por sucursal en productos:** `branchId` ajeno a `storeId`, o `branchId` sin `storeId`, responde 400 `BRANCH_NOT_IN_STORE`. El filtro se aplica antes de `cheapestPerProduct`.
- **Coordenadas:** si llega solo una de las dos, 400 `LOCATION_INCOMPLETE`.
- **Horario:** si no es válido, 400 `INVALID_OPENING_HOURS`.
- **Servicios en el portal:**
  - Leer usa `STORE_READ` (con `?includeInactive=true` para ver todos); escribir usa `STORE_UPDATE`.
  - La respuesta incluye `version`. Si no coincide, 409 `SERVICE_VERSION_CONFLICT`.
- **`verified`:** va fijo en `true` para los almacenes publicados.
- **`GET /marketplace/services`:** no se implementó.
- **BD (migración `sve_unique_by_branch_service`, 2026-11-11):** `uq_sve_visitor_event` es ahora `(day, store_id, product_id, branch_id, service_id, kind, visitor_hash) nulls not distinct`. Se cuenta un clic por visitante, día, sucursal, servicio y producto.
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** `core/organization` (servicios, `BranchUpdateRequest` con `whatsapp/openingHours/lat/lng`, consultas públicas), `marketplace/stores/*` (perfil ampliado, `/branches`), `marketplace/catalog/ProductBrowseController.java` (`branchId`), `core/analytics/{VisibilityRecorder,VisibilityQueries,VisibilityReport}.java`, `operations/metrics/TrafficController.java`; tests correspondientes.
- **Contexto:** contratos «API pública», «API del portal» y reporte ampliado. Depende de T1 y T3 (`whatsappEnabled`, `featuredProductIds`).
- **Aceptación:** `./mvnw -q test -Dtest=StoreServicesTest,PublicBranchesTest,StorePublicProfileTest,VisibilityRecorderTest,VisibilityQueriesTest` en verde: sucursal de otro almacén → 400/no se registra; `openNow` correcto con reloj fijo; `whatsapp` cae al del almacén; reporte con `whatsappClicks` y `byBranch`.

### T5 — Perfil público v2, mapa y WhatsApp
- **Modelo:** Sonnet · **Agente:** frontend (cuenta de frontend)
- **Archivos:** `features/marketplace/pages/store-page.tsx` y nuevos componentes en `features/marketplace/components/store/` (`store-header`, `branch-map`, `branch-card`, `whatsapp-button`, `directions-menu`, `services-list`), `pages/product-page.tsx`, `api/*`, `lib/visibility.ts`; `package.json` (`leaflet`, `react-leaflet`, **autorización**).
- **Contexto:** contratos «API pública», «Enlaces» y «Frontend». Depende de T4.
- **Aceptación:** build y lint en verde; manual: banner y redes visibles; «Cómo llegar» abre Google/Waze con el destino; WhatsApp abre `wa.me` con producto y enlace y registra un `WHATSAPP_CLICK`; filtro por sucursal cambia productos; mapa sin scroll horizontal a 360 px y con alternativa en lista; menús operables con teclado.

### T6 — Portal: sucursales, servicios, configuración y métricas
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** `features/portal/pages/{store-profile-page,branches…,services-page,store-settings-page,intelligence-visibility-page}.tsx`, navegación y API del portal.
- **Contexto:** contratos «API del portal» y «Configuraciones». Depende de T3, T4, T5 (mapa reutilizable).
- **Aceptación:** build y lint; manual: fijar punto en el mapa guarda lat/lng; horario inválido marca el campo; CRUD de servicios; mensaje de WhatsApp sin `{enlace}` muestra error; visibilidad muestra clics de WhatsApp por sucursal y producto.

### T7 — Cuenta y autenticación
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** `features/auth/*` (verificar correo con código, reenviar, manejo de `EMAIL_NOT_VERIFIED`, pantalla 2FA), `features/account/pages/settings-page.tsx`, rutas.
- **Contexto:** contratos «API de cuenta e identidad» y «Configuraciones». Depende de T2 y T3.
- **Aceptación:** build y lint; manual: login sin verificar lleva a `/verificar-correo` con el correo enmascarado; reenviar deshabilitado 60 s; código de 6 dígitos con `inputmode="numeric"` y pegado; activar 2FA pide código; login con 2FA pide código.

### T8 — QA del diff
- **Modelo:** Opus · **Agente:** qa-seguridad
- **Contexto:** solo el diff de T2–T7: enumeración de cuentas (login, reenvío, código), fuerza bruta de códigos, IDOR en servicios/sucursales/settings, inflado de `WHATSAPP_CLICK`, inyección en `whatsappMessage`/URLs `wa.me` y mapas, XSS en horarios/notas, datos personales en eventos.
- **Aceptación:** informe clasificado; tests de T2–T4 con `-Dtest=` explícito.

## Riesgos y puntos a verificar

- **Teselas OSM:** su política no admite tráfico alto; si crece, cambiar a MapTiler/Stadia (misma API de Leaflet) con clave en `.env`.
- **Correos HTML:** revisar entregabilidad (SPF/DKIM del remitente) y que el cliente no corte el código; probar en Gmail/Outlook.
- **2FA y sesiones existentes:** activar 2FA no cierra sesiones abiertas; decidir si `logout/others` se ofrece al activarlo.
- **WhatsApp inflado:** el clic no garantiza conversación; rotular «clics para cotizar».
- **Coordenadas faltantes:** sucursales antiguas sin lat/lng; el portal debe pedir completarlas (checklist de perfil).
- **`WebSecurityConfig`:** T2, T3 y T4 lo tocan; ejecutar en secuencia y con aprobación.
- **Frontend en otra cuenta:** T5–T7 usan este documento como contrato.
