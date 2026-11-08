# Plan: login social, auditoría y estadísticas de plataforma

## Objetivo y alcance
- **Entra:** ingreso con Google y Facebook; historial de auditoría para ROOT; estadísticas de plataforma (tráfico, cuentas, accesos, comercio).
- **No entra:** otros proveedores (Apple, Microsoft), exportación a SIEM, analítica de terceros (GA, Vercel Analytics), mapas de calor.

## Componentes
| Capa | Componente | Responsabilidad |
|---|---|---|
| BD (Supabase) | `account_identities` | Vínculo cuenta ↔ proveedor social |
| BD | `traffic_events` | Visitas first-party, sin IP ni datos personales |
| BD | índices en `audit_logs` | Consulta por fecha, acción y actor |
| Backend `core.identity` | `SocialLoginService` + `GoogleTokenVerifier`, `FacebookTokenVerifier` | Verificar token del proveedor en servidor, vincular o crear cuenta, emitir sesión con `SessionIssuer` |
| Backend `core.audit` | `AuditService.record(AuditEvent)` | Registro genérico; instrumentar acciones relevantes |
| Backend `operations.audit` | `AuditAdminController` | Consulta paginada para ROOT |
| Backend `operations.metrics` | `PlatformStatsController`, `TrafficController` | Ingesta de visitas y agregados |
| Frontend `auth` | Botones Google/Facebook | Obtener token del proveedor y canjearlo |
| Frontend `admin` | `/admin/auditoria`, `/admin/estadisticas` | Pantallas de la consola root |

## Contratos

### Login social
`POST /api/v1/auth/social/{provider}` — `provider` ∈ `google | facebook`. Público.
```json
// request
{ "token": "<id_token de Google | access_token de Facebook>",
  "registration": { "name": "opcional", "surname": "opcional", "identification": "…", "dateBirth": "AAAA-MM-DD", "numberPhone": "…" } }
// 200: mismo LoginResponse que POST /auth/login (access en el cuerpo, refresh en cookie)
```
`registration` solo tras `409 SOCIAL_REGISTRATION_REQUIRED` (cuenta nueva; `details` = `{ email, name, surname }` del proveedor para prellenar).
Errores: `401 SOCIAL_TOKEN_INVALID`, `401 UNAUTHORIZED` (cuenta inactiva o bloqueada), `400 SOCIAL_EMAIL_REQUIRED`, `400 SOCIAL_NAME_REQUIRED`, `404 SOCIAL_PROVIDER_DISABLED` (sin credenciales), `409 SOCIAL_LINK_REQUIRED` (correo existente que el proveedor no garantiza), `409 IDENTIFICATION_ALREADY_EXISTS`.

Reglas:
- Google: verificar firma, `aud = GOOGLE_CLIENT_ID`, `iss`, `exp`; vincula a cuenta existente solo si `email_verified = true`.
- Facebook: `GET graph.facebook.com/debug_token` con app token (`app_id` debe coincidir) y `/me?fields=id,name,email`; nunca vincula automáticamente a una cuenta existente (→ 409).
- Cuenta nueva: tipo CUSTOMER, activa, contraseña aleatoria inutilizable (puede definir una con "olvidé mi contraseña"). `persons` exige identificación y fecha de nacimiento, por eso el paso `registration`.
- Variables en `.env` raíz: `GOOGLE_CLIENT_ID`, `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET`; frontend `VITE_GOOGLE_CLIENT_ID`, `VITE_FACEBOOK_APP_ID`.

`account_identities`: `id uuid pk, account_id uuid fk, provider varchar(20), subject varchar(255), email varchar(255), created_at, deleted_at`; `unique(provider, subject) where deleted_at is null`.

### Auditoría
`GET /api/v1/admin/audit?from&to&action&entityType&actor&page=1&size=50` (ROOT; máx. 200; rango máx. 366 días).
```json
{ "page": { "items": [ {
    "id": "uuid", "occurredAt": "ISO", "action": "PLAN_UPDATED",
    "actor": { "accountId": "uuid|null", "email": "…|null" },
    "entityType": "PLAN", "entityId": "uuid|null",
    "ipAddress": "…|null", "metadata": { } } ],
  "page": 1, "size": 50, "total": 0 } }
```
`GET /api/v1/admin/audit/actions` → `[{ "action": "PLAN_UPDATED", "entityType": "PLAN" }]` (catálogo para filtros).

Acciones a registrar (además de las 4 actuales): `LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `SOCIAL_LOGIN`, `SOCIAL_LINKED`, `PASSWORD_RESET`, `STORE_APPLICATION_REVIEWED`, `PLAN_CREATED/UPDATED/DISABLED`, `PLATFORM_PROMOTION_CREATED/UPDATED/DELETED`, `CATEGORY_*`, `MANUFACTURER_*`, `STAFF_INVITED/ROLE_CHANGED/REMOVED`, `SUBSCRIPTION_CHANGED`, `PAYMENT_WEBHOOK_REJECTED`. `metadata` nunca guarda contraseñas, tokens ni datos de pago.

Índices: `audit_logs(created_at desc)`, `(action, created_at desc)`, `(account_id, created_at desc)`.

### Tráfico
`POST /api/v1/events/pageview` (público, 204; límite 60/min por IP, cuerpo máx. 1 KB).
```json
{ "anonymousId": "<id de anonymous-session.ts>", "path": "/buscar", "referrer": "https://…" }
```
Servidor guarda: `day date, path_group varchar(60)` (ruta normalizada: `/producto/:id`), `visitor_hash` (SHA-256 de anonymousId + sal diaria), `account_id` si hay sesión, `device` (mobile/desktop por user-agent), `referrer_host`. Sin IP ni user-agent completo (Ley 1581).

### Estadísticas de plataforma
`GET /api/v1/admin/analytics/platform?from&to&granularity=DAY|WEEK|MONTH` (ROOT).
```json
{ "from": "AAAA-MM-DD", "to": "AAAA-MM-DD",
  "traffic":  { "visits": 0, "uniqueVisitors": 0, "series": [{ "date": "", "visits": 0, "visitors": 0 }],
                "topPaths": [{ "path": "", "visits": 0 }], "referrers": [{ "host": "", "visits": 0 }],
                "devices": { "mobile": 0, "desktop": 0 } },
  "accounts": { "total": 0, "new": 0, "byType": [{ "type": "CUSTOMER", "count": 0 }],
                "verifiedRate": 0.0, "locked": 0, "socialShare": [{ "provider": "google", "count": 0 }],
                "series": [{ "date": "", "new": 0 }] },
  "access":   { "logins": 0, "failedLogins": 0, "dau": 0, "wau": 0, "mau": 0,
                "series": [{ "date": "", "logins": 0, "failed": 0 }] },
  "commerce": { "orders": 0, "gmv": 0, "averageTicket": 0, "activeStores": 0,
                "pendingApplications": 0, "subscriptionsByPlan": [{ "plan": "PRO", "count": 0 }], "mrr": 0 } }
```
`dau/wau/mau`: cuentas distintas con login exitoso o sesión renovada en 1/7/30 días hasta `to`.

## Decisiones
- **Token del proveedor canjeado en el backend** (Google Identity Services / Facebook SDK en el navegador) en vez del flujo `oauth2-client` con redirección: encaja con el JWT propio y la SPA, sin sesiones de servidor.
- **Facebook no vincula por correo**: Meta no garantiza correo verificado; evita toma de cuentas.
- **Tráfico first-party** en vez de GA/Vercel Analytics: los datos no salen de la plataforma y no exige banner de cookies de terceros.
- **Auditoría sobre `audit_logs` existente** con un `record` genérico, en vez de tabla nueva.

## Pasos

1. **BD (`database`, Sonnet)** — En Supabase `autopartco-dev`: `account_identities`, `traffic_events` e índices de `audit_logs`.
2. **Backend (`backend`)** — En este orden, cada uno con sus pruebas unitarias (`-Dtest=<Clase>`):
   - Login social `POST /auth/social/{provider}` (Opus).
   - Auditoría: `AuditService.record`, instrumentación y `GET /admin/audit` (Sonnet).
   - Tráfico `POST /events/pageview` y `GET /admin/analytics/platform` (Sonnet).
3. **Frontend (`frontend`, Sonnet)** — Botones Google/Facebook en ingreso y registro; páginas `/admin/auditoria` y `/admin/estadisticas`; envío de `pageview` al navegar (no en `/admin` ni `/portal`).

Criterio de cierre: `tsc -b` y `oxlint` limpios, pruebas backend de cada clase nueva en verde.

## Riesgos y verificación
- **Credenciales externas:** el usuario debe crear el cliente OAuth en Google Cloud y la app en Meta (dominio `autopartco.co`, URIs de origen) y poner las claves en `.env`; Facebook exige revisión de la app para el permiso `email` en producción.
- **Ley 1581:** aviso de privacidad debe mencionar el ingreso social y la medición de visitas; verificar que no se guarde IP en `traffic_events`.
- **Volumen de `traffic_events`:** si crece, añadir agregado diario y purga a 13 meses (decisión puntual cuando aplique).
- **Login sin contraseña:** las cuentas creadas por proveedor deben poder recuperar acceso; verificar el flujo de recuperación con ellas.
