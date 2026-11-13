# Plan: cuenta unificada, pedidos, notificaciones y ajustes de UI

Fecha: 2026-11-11. Estado: ejecutado (T0–T11, PRs #141–#149; ver §7). Secuencia: `database` → `backend` → `frontend` → `qa-seguridad`.
SQL nuevo: se aplica en Supabase `autopartco-dev` (no se escribe en el repo). UI: cumple `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md` (§17, §20.15).

## 1. Objetivo y alcance
Entra:
1. Perfil público de almacén: logo **encima** del banner; mapa de sedes en todos los almacenes (sedes sin coordenadas).
2. Cabecera móvil: el selector de ubicación no se solapa con el imagotipo.
3. Bandeja de notificaciones: compacta, cada notificación enlaza a su destino, «marcar todo como leído», limpiar (archivar) y página de histórico.
4. Pedidos del comprador: lista, detalle con productos, comprobante de compra y factura del almacén (si la adjunta); sugerencias para calificar lo recibido (en el sitio, notificación y correo).
5. «Mi cuenta» común a todos los roles (cliente, almacén, admin/root): perfil, seguridad, direcciones, preferencias, privacidad.
6. Registro con Google (corregido en `fix/registro-google`, ver T0).

No entra: WhatsApp (requiere WhatsApp Business API de pago: queda la preferencia de canal y la interfaz `ReminderChannel`, sin implementación); factura electrónica DIAN emitida por Autopartco (la emite el almacén; aquí solo se adjunta); cambio de correo con migración de identidades sociales (solo verificación del nuevo correo).

## 2. Componentes
- **BD:** `notifications` (+`archived_at`, `kind`), `order_invoices`, `review_reminders`, preferencias en `accounts.configuration`, `account_deletion_requests`.
- **Backend:** `core/notification` (bandeja paginada, enlaces en todos los emisores), `core/identity` (cuenta: contraseña, correo, preferencias, identidades sociales, exportación, baja), `marketplace/tracking` (comprobante, factura), `marketplace/reviews` (recordatorios programados).
- **Frontend:** `features/account/` nuevo (`/cuenta/*`), `features/notifications` (menú + página), `features/orders` (detalle ampliado), ajustes en `store-header`, `branches-panel`, `site-header`, `account-menu-items`.

## 3. Contratos

**Notificaciones** (todas autenticadas, propias de la cuenta):
- `GET /me/notifications?status=active|archived&page=0&size=20` → `Page<NotificationResponse>`; `NotificationResponse` añade `kind: string`.
- `GET /me/notifications/unread-count` → `{count:number}`.
- `PATCH /me/notifications/{id}/read` (existe) · `POST /me/notifications/read-all` → 204.
- `POST /me/notifications/{id}/archive` · `POST /me/notifications/archive-read` (archiva todas las leídas) → 204. Archivar no borra: el histórico es `status=archived`. Retención: archivadas > 180 días se eliminan por tarea programada.
- Todo emisor envía `link` interno. Mapa obligatorio: venta cambia de estado → `/mis-pedidos/{orderId}` (comprador) y `/portal/{storeId}/pedidos/{saleId}` (almacén); pregunta → ya enlaza; reseña por calificar → `/cuenta/calificar?order={orderId}`. `kind`: `ORDER_STATUS | QUESTION | ANSWER | REVIEW_REQUEST | REVIEW_REPLY | STORE_ALERT | ACCOUNT_SECURITY | SYSTEM`.

**Pedidos del comprador:**
- `GET /me/orders` y `GET /me/orders/{id}` (existen). El detalle debe incluir: ítems (`productId, name, imageUrl, quantity, unitPrice, subtotal`), por venta `saleId, storeId, storeName, status, deliveredAt`, `reviewable:boolean` por ítem y `invoice:{fileName,url}|null` por venta.
- `GET /me/orders/{id}/receipt` → `text/html` imprimible (comprobante de compra, no factura). Solo el dueño.
- Almacén: `POST /stores/{storeId}/sales/{saleId}/invoice` multipart `file` (PDF ≤5 MB) · `DELETE` igual. Permiso `ORDER_UPDATE`. URL firmada temporal (Supabase Storage, bucket privado).
- `GET /account/reviews/pending` (existe): lo usa la sección «Por calificar».

**Recordatorios de calificación:**
- Al pasar una venta a `DELIVERED`: notificación `REVIEW_REQUEST` inmediata.
- Tarea diaria: a los 3 y 10 días de entregado, si sigue sin reseña y la preferencia lo permite, correo (plantilla `review-reminder`) — máximo 2 por venta, registro en `review_reminders(sale_id, channel, sent_at)` único `(sale_id, channel, attempt)`.
- Interfaz `ReminderChannel { String code(); boolean enabled(); void send(...); }` con `EmailReminderChannel`; `WHATSAPP` queda sin implementar.

**Mi cuenta** (`/me`, todos los roles):
- `GET/PATCH /me/profile` (existen).
- `POST /me/password` `{currentPassword,newPassword}` → 204; revoca las demás sesiones; con cuenta social sin contraseña conocida → 409 `PASSWORD_NOT_SET` (usar recuperación).
- `POST /me/email` `{newEmail,currentPassword}` → 202; envía código al nuevo correo · `POST /me/email/confirm` `{code}` → 204.
- `GET/PUT /me/preferences` → `{notifications:{email:{orders,reviews,questions,promotions},inApp:{promotions}}, reviewReminders:boolean, marketingConsent:boolean, defaultCity?:string}`; se guarda en `accounts.configuration.preferences`. Valores por defecto: todo `true` salvo `promotions` y `marketingConsent` (`false`, Ley 1581).
- `GET /me/identities` → `[{provider,email,linkedAt}]` · `DELETE /me/identities/{provider}` (409 `LAST_SIGN_IN_METHOD` si no hay contraseña utilizable).
- Seguridad: `/me/sessions` (existe), 2FA `/me/two-factor/*` (existe).
- Direcciones: `/me/addresses` (existe).
- `GET /me/data-export` → JSON con perfil, direcciones, pedidos, reseñas, preguntas (Ley 1581, derecho de acceso).
- `POST /me/deletion-request` `{currentPassword?, reason?}` → 202; crea `account_deletion_requests` y bloquea si es dueño de un almacén activo (409 `STORE_OWNER_CANNOT_DELETE`). Ejecución manual por admin (fuera de alcance).

**Menú de cuenta común** (`account-menu-items.ts`): grupo compartido para todo rol autenticado: `Mi cuenta (/cuenta)`, `Mis pedidos`, `Por calificar`, `Notificaciones (/cuenta/notificaciones)`; luego los grupos propios del rol (portal, admin, beneficios, solicitud de almacén).

**Rutas frontend nuevas:** `/cuenta` (resumen) · `/cuenta/perfil` · `/cuenta/seguridad` (contraseña, correo, 2FA, sesiones, identidades) · `/cuenta/direcciones` · `/cuenta/preferencias` · `/cuenta/privacidad` (exportar, solicitar baja) · `/cuenta/notificaciones` (histórico con pestañas Activas/Archivadas) · `/cuenta/calificar` (existe).

## 4. Decisiones
- **Archivar en vez de borrar** notificaciones: permite «limpiar» y conservar histórico. Descartado: borrado duro (pierde histórico).
- **Preferencias en `accounts.configuration`** (jsonb ya existente). Descartado: tabla nueva (más migración para datos sin consultas cruzadas).
- **Comprobante HTML imprimible** en vez de PDF generado: sin dependencias nuevas; el navegador lo guarda como PDF.
- **Factura = adjunto del almacén**: el obligado a facturar en Colombia es el vendedor.
- **Mapa siempre visible**: sedes sin coordenadas se geocodifican al guardar; si falla, se usa el centroide del municipio y el marcador se marca «ubicación aproximada». Coordenadas pasan a ser obligatorias al crear/editar sede (con pin arrastrable).
- **Registro social**: el intento de ingreso exitoso se registra en la transacción del llamador; `REQUIRES_NEW` queda solo para fallidos.

## 5. Tareas

**T0 — Registro con Google (hecho, sin commit).** Rama `fix/registro-google`. Causa: `LoginAttemptService.record` corre en `REQUIRES_NEW`; en el registro social la cuenta aún no está confirmada y el insert de `login_attempts` falla por FK → 500 (en BD: 0 `USER_REGISTERED_SOCIAL`, solo vínculos a cuentas existentes). Arreglo: `recordSuccessful` en la transacción actual. `SocialLoginServiceTest` en verde. Pendiente: commit, PR a `develop`, despliegue y prueba real de alta con Google.

**T1 — UI rápida del perfil de almacén y cabecera móvil.** Sonnet · `frontend` · `features/marketplace/components/store/store-header.tsx`, `app/site-header.tsx`, `features/location/components/location-picker.tsx`. El logo (`-mt-12 sm:-mt-16`) queda debajo del banner porque el banner es `relative`: envolver el logo en `relative z-10` (con borde blanco y sombra según §design). En <640 px el disparador de ubicación muestra solo el icono + ciudad truncada (`max-w-[8rem] truncate`) y el contenedor del logo usa `shrink-0`. Aceptación: a 360/375/414 px no hay solapamiento (captura); el logo se ve completo sobre el banner en sm/lg.

**T2 — Coordenadas de sedes.** Sonnet · `database` + `backend` + `frontend` · 2 de 16 sedes sin `latitude/longitude`. (a) Backend: al crear/editar sede, geocodificar dirección; si falla, centroide de municipio y `metadata.approximate=true`; exigir coordenadas finales. (b) Rellenar las 2 sedes existentes (SQL en Supabase tras revisarlas). (c) `branches-panel.tsx`: con `approximate` muestra «Ubicación aproximada». (d) Formulario de sede en portal con pin arrastrable. Aceptación: toda sede pública tiene `point`; todos los perfiles muestran mapa.

**T3 — BD notificaciones y recordatorios.** Haiku · `database` (Supabase) · `notifications`: `archived_at timestamptz null`, `kind varchar(30) not null default 'SYSTEM'` con check de los valores del contrato, índice `(account_id, archived_at, created_at desc)`; tabla `review_reminders`, tabla `order_invoices(sale_id unique, storage_path, file_name, uploaded_by, created_at, deleted_at)`, tabla `account_deletion_requests(account_id, reason, status, created_at, processed_at)`. Aceptación: `list_tables` muestra columnas; inserts de prueba respetan checks.

**T4 — Backend bandeja de notificaciones.** Sonnet · `backend` · `core/notification/NotificationService.java`, `core/identity/web/MeNotificationController.java`, emisores (`core/order/SaleStatusService.java`, `marketplace/questions/QuestionNotifier.java`, reseñas). Implementar endpoints §3 Notificaciones; `notify(...)` recibe `kind`; `SaleStatusService` envía enlaces al comprador y al almacén. Aceptación: tests `-Dtest=NotificationServiceTest,MeNotificationControllerTest` (paginación, archivar ajenas → 404, read-all, enlaces de venta).

**T5 — Frontend bandeja.** Sonnet · `frontend` · `features/notifications/*`, ruta `/cuenta/notificaciones`. Menú: máximo 6, alto máximo con scroll interno, contador desde `unread-count`, acciones «Marcar todo como leído» y «Limpiar leídas», pie «Ver todas». Ítems sin `link` no parecen clicables. Página con pestañas Activas/Archivadas y paginación. Aceptación: cada notificación de venta navega a su pedido; limpiar mueve a Archivadas; lint/build en verde.

**T6 — Backend pedidos, comprobante y factura.** Sonnet · `backend` · `marketplace/tracking/*`, `storesuite` ventas. Ampliar `BuyerOrderDetail` (§3), `GET /me/orders/{id}/receipt`, subida/borrado de factura con bucket privado y URL firmada. Aceptación: tests de propiedad (otro comprador → 404), tipo/tamaño de archivo, `reviewable` correcto.

**T7 — Backend recordatorios de calificación.** Sonnet · `backend` · `marketplace/reviews`, `core/notification`. Notificación al pasar a `DELIVERED`; `@Scheduled` diario con `ReminderChannel` (correo), respeta `preferences.reviewReminders` y `notifications.email.reviews`, idempotente con `review_reminders`. Aceptación: test con reloj fijo: días 3 y 10 envían, día 11 no, con reseña no envía.

**T8 — Backend cuenta.** Opus (seguridad) · `backend` · `core/identity`. Endpoints §3 Mi cuenta (contraseña, correo, preferencias, identidades, exportación, solicitud de baja). Auditoría en cada cambio; correo de aviso en cambio de contraseña/correo; rate limit en `/me/password` y `/me/email`. Aceptación: tests por endpoint incluidos casos 409 y contraseña incorrecta → 400 `INVALID_PASSWORD`.

**T9 — Frontend «Mi cuenta» y menú común.** Sonnet · `frontend` · `features/account/` nuevo, `features/auth/lib/account-menu-items.ts`, `app/router.tsx`. Layout con navegación lateral (móvil: lista), secciones §3, grupo común del menú para todos los roles (incluido almacén y admin). Aceptación: las tres clases de cuenta ven el mismo grupo común; cada sección guarda y muestra errores del servidor.

**T10 — Frontend pedidos y calificar.** Sonnet · `frontend` · `features/orders/*`, `features/reviews/*`. Detalle con productos, estado por almacén, «Imprimir comprobante», «Descargar factura», botón «Calificar» por ítem entregado; banner «Tienes N compras por calificar» en `/mis-pedidos` y en `/cuenta`. Portal: subir factura en `portal/pages/order-detail-page.tsx`. Aceptación: flujo entregado → calificar en ≤2 clics desde la notificación.

**T11 — QA acotado.** Sonnet · `qa-seguridad` · solo el diff de T0–T10: IDOR en notificaciones/pedidos/facturas, enlaces internos, cambio de correo/contraseña, subida de archivos. Aceptación: informe sin hallazgos altos abiertos.

## 6. Riesgos
- Cambio de correo con cuentas vinculadas a Google: el `subject` sigue siendo válido; verificar que el login social no recree cuenta.
- Correos de recordatorio: cumplir preferencias y enlace de baja (Ley 1581).
- Geocodificación: dependencia externa; definir proveedor (el que ya use `location`) antes de T2.
- T3 y T6 tocan esquema: aplicar en Supabase antes de desplegar el backend correspondiente.

## 7. Estado tras la ejecución (2026-11-13)
- Cuentas creadas con Google: `accounts.configuration.passwordSet = false` hasta que la persona crea su contraseña con la recuperación. Sin contraseña: `/me/password` y `/me/email` → 409 `PASSWORD_NOT_SET`; desvincular → 409 `LAST_SIGN_IN_METHOD`; la solicitud de baja no exige contraseña y avisa por correo y notificación. `GET /me/profile` expone `passwordSet` (rama `fix/cuenta-social-sin-contrasena`).
- Desviación aceptada: no hay interfaz `ReminderChannel`; `ReviewReminderJob` envía el correo directamente (se introduce cuando exista un segundo canal).
- Pendiente: el recordatorio por correo solo respeta `reviewReminders`, no `notifications.email.reviews`.
