# ADR 0003 — Las operaciones sensibles exigen identidad autenticada

- Estado: **Aceptado** (decisión del dueño del producto)
- Fecha: 2026-10-16
- Autor: agente `arquitecto`
- Relacionado: `docs/architecture/2026-10-16-endurecimiento-identidad-plan.md`, `docs/architecture/2026-10-15-configuracion-almacen-sin-token-plan.md` (su flujo con token queda reemplazado)

## 1. Contexto

La auditoría encontró que `POST /store-applications/setup` acepta solo un token de correo, emite JWT y refresh de 30 días y promueve a `STORE_MANAGER` a un anónimo. Quien tenga el enlace (reenviado, en logs, en un historial) obtiene identidad y rol. Otras rutas (aceptar invitaciones, verificar correo) tienen el mismo patrón de fondo: el token sustituye a la identidad.

Decisión del dueño del producto: «no es seguro que exista un enlace que permita cambios importantes sin autenticación; cualquier enlace u operación que requiera autenticación debe reconocer quién la hace y si es el usuario correcto o tiene permiso; esta filosofía aplica a todo el sistema».

## 2. Decisión

1. **Cambio importante = sesión + autorización en servidor.** Roles, almacén, suscripción, datos de cuenta, permisos y colaboradores exigen sesión y comprobar `actor == dueño` o permiso explícito sobre el recurso (organización/sucursal). Un token de correo **nunca** es credencial de sesión, no eleva roles y no emite JWT ni refresh.
2. **Los enlaces de correo son de navegación.** Apuntan a una página del frontend que exige iniciar sesión con la cuenta destinataria y vuelve a la ruta original mediante un parámetro de retorno seguro (solo rutas relativas internas que empiecen por `/` y no por `//`, validadas contra lista de prefijos). Si hay sesión de otra cuenta, se avisa y se ofrece cambiar de cuenta. Un token en la URL, si existe, es solo una pista que el servidor ignora.
3. **Excepción acotada: verificar correo, desbloqueo y restablecer contraseña**, porque la persona aún no puede autenticarse. Requisitos: token de un solo uso con lock al consumirlo, TTL corto, almacenado como hash, reemitir invalida los pendientes, la acción es `POST` tras clic en una página del frontend (nunca efectos en `GET`, por prefetch de correo), transiciones solo `PENDING→ACTIVE` y `LOCKED→ACTIVE`, no emite sesión ni da acceso más allá de su propósito, con rate limit.
4. **Invitaciones de personal.** Cuenta existente: exige sesión de esa cuenta para aceptar. Cuenta nueva: establece su contraseña en el mismo acto, con consumo atómico del token y TTL ≤ 72 h. Aceptar no reactiva cuentas `INACTIVE` ni activa `PENDING` sin verificación de correo. Preview y accept devuelven errores uniformes.
5. **Rate limit por IP y por correo** en endpoints públicos (`/auth/**`, `/store-applications/**`, `/staff-invitations/**`, multipart público).

Regla de revisión: toda funcionalidad nueva que cambie estado de negocio a partir de un enlace debe citar este ADR y justificar por qué no cae en el punto 1.

## 3. Alternativas descartadas

- **Token como credencial limitada («magic link» de un solo uso).** Cómodo, pero convierte el correo en factor único, no distingue destinatario de quien reenvía el enlace y exige controlar el alcance del token en cada endpoint.
- **Mantener token y exigir además sesión opcional (`OptionalSession`).** Dos caminos de autorización a mantener; el camino anónimo sigue siendo el más débil.
- **Rol intermedio con acceso parcial al portal.** Cambia el modelo de permisos sin resolver el problema de identidad.

## 4. Consecuencias

- Un paso más para el usuario (iniciar sesión antes de configurar el almacén o aceptar una invitación); se compensa con retorno automático.
- Se eliminan `POST /store-applications/setup`, `/setup/resend`, `OptionalSession` y el `BearerTokenResolver` especial; el frontend pierde el flujo con token.
- Los correos ya enviados con `?token=` siguen llegando a `/seller/setup`, que ignora el token.
- Requiere páginas de frontend para verificación, desbloqueo e invitación, y un limitador de peticiones (decisión de implementación sujeta a aprobación si añade dependencia).
- Sin migración de esquema prevista.
