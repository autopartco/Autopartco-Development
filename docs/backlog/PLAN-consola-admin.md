# Plan: consola de administración ampliada

Fecha: 2026-11-15. Secuencia: `database` (Supabase) → `backend` → `frontend` → verificación.

## Objetivo y alcance
Entra:
- Cuentas de plataforma con roles y permisos (administradores, soporte técnico, analistas, riesgo).
- Pantallas nuevas: pagos atípicos (Isolation Forest), modelos predictivos, almacenes, cuentas, pedidos, pagos y equipo de plataforma.
- Las secciones actuales filtradas por permiso.

No entra: suspender almacenes, reembolsos, edición de pedidos ni reentrenar modelos desde la consola.

## Decisiones
- **Control de acceso de plataforma en tablas propias**: `platform_roles`, `platform_permissions`, `platform_role_permissions` y `platform_staff`. No se usa `permissions`, porque el rol «Propietario» de cada almacén nuevo recibe todos sus registros y heredaría los permisos de plataforma.
- **Tipo de cuenta `STAFF`.** El JWT lleva `ROLE_STAFF` y los códigos `PLATFORM_*` de su rol. `ROOT` puede todo. Los cambios de rol aplican al renovar el token (≤ duración del token de acceso); si se desactiva un miembro, se revocan sus sesiones.
- **`/api/v1/admin/**` acepta `ROLE_ROOT` o `ROLE_STAFF`.** Cada endpoint exige `hasAnyAuthority('ROLE_ROOT', 'PLATFORM_X')`.
- **Gestionar el equipo y los roles es solo de `ROOT`**, para evitar escalada de privilegios.
- **Alta de miembros:** `ROOT` captura los datos personales, el correo y el rol. La cuenta se crea `ACTIVE` con una contraseña aleatoria y se envía el correo de recuperación para que la persona defina la suya.
- **Lecturas de admin con SQL directo** en `operations.*`. Las acciones sobre cuentas pasan por la API pública de `core.identity`.

## Permisos
| Código | Habilita |
|---|---|
| `PLATFORM_DASHBOARD_READ` | Resumen |
| `PLATFORM_APPLICATIONS_REVIEW` | Solicitudes de almacén |
| `PLATFORM_STORES_READ` | Almacenes |
| `PLATFORM_ACCOUNTS_READ` / `PLATFORM_ACCOUNTS_MANAGE` | Cuentas / bloquear, activar, cerrar sesiones |
| `PLATFORM_ORDERS_READ` | Pedidos y pagos |
| `PLATFORM_PAYMENT_RISK_REVIEW` | Pagos atípicos (revisar y marcar el resultado) |
| `PLATFORM_CATALOG_MANAGE` | Catálogo y reindexado |
| `PLATFORM_PLANS_MANAGE` / `PLATFORM_PROMOTIONS_MANAGE` / `PLATFORM_ADS_MANAGE` | Comercial |
| `PLATFORM_MODERATION` | Reseñas y preguntas |
| `PLATFORM_ANALYTICS_READ` | Estadísticas, clientes, búsquedas y modelos |
| `PLATFORM_AUDIT_READ` | Auditoría |

Roles predefinidos:
- **Administrador**: todos los permisos.
- **Soporte técnico**: resumen, almacenes, cuentas (leer y gestionar), pedidos, solicitudes y moderación.
- **Analista**: resumen, analítica, almacenes y pedidos.
- **Riesgo y pagos**: resumen, pedidos, pagos atípicos y lectura de cuentas.

## Endpoints nuevos (`/api/v1/admin`)
- **Mi acceso:** `GET /me` → `{root, roleName, permissions[]}`.
- **Pagos atípicos:**
  - `GET /payment-risk?decision=&reviewed=&page=` → evaluaciones con monto, pedido, cliente, puntuación y umbral.
  - `GET /payment-risk/summary`.
  - `POST /payment-risk/{id}/outcome {outcome}`, con `LEGIT` o `FRAUD_CONFIRMED`.
- **Modelos:** `GET /ml/models` → versión, algoritmo, métricas, línea base y último lote.
- **Almacenes:** `GET /stores?q=&page=` y `GET /stores/{id}`.
- **Cuentas:**
  - `GET /accounts?q=&type=&state=&page=` y `GET /accounts/{id}` (sesiones e ingresos recientes).
  - `POST /accounts/{id}/lock`, `/unlock` y `/revoke-sessions`.
- **Pedidos y pagos:** `GET /orders?q=&status=&page=`, `GET /orders/{id}` y `GET /payments?status=&provider=&page=`.
- **Equipo (solo ROOT):**
  - Miembros: `GET/POST /staff`, `PATCH /staff/{accountId}` (rol o activo) y `DELETE /staff/{accountId}`.
  - Roles: `GET/POST /staff/roles`, `PUT/DELETE /staff/roles/{id}` y `GET /staff/permissions`.

## Riesgos
- Los permisos viajan en el token: un cambio tarda hasta la renovación. Mitigación: al desactivar un miembro o quitarle el rol se revocan sus sesiones.
- Los nuevos `@PreAuthorize` tocan seguridad. Las pruebas por controlador verifican ROOT, miembro con permiso y miembro sin permiso.
