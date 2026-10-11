# Seguridad del Backend

## Índice

- [Propósito](#proposito)
- [Política general](#politica-general)
- [Endpoints públicos actuales](#endpoints-publicos-actuales)
- [Registro con verificación de correo](#registro-con-verificacion-de-correo)
- [Passwords](#passwords)
- [Tokens de verificación](#tokens-de-verificacion)
- [Configuración de correo](#configuracion-de-correo)
- [CORS](#cors)
- [Reglas para nuevos endpoints](#reglas-para-nuevos-endpoints)

## Proposito

La seguridad del backend se centraliza en:

```text
src/main/java/com/autopartco/backend/config/WebSecurityConfig.java
```

La API opera sin sesiones HTTP (`STATELESS`), con CSRF deshabilitado y con JWT como servidor de recursos para endpoints protegidos.

## Politica general

Todo endpoint queda protegido por defecto:

```java
.anyRequest().authenticated()
```

Los endpoints publicos deben declararse de forma explicita.

## Endpoints publicos actuales

| Metodo | Ruta | Motivo |
| --- | --- | --- |
| `GET` | `/actuator/health` | Salud de la aplicacion. |
| `GET` | `/api/v1/test/public` | Endpoint publico de prueba. |
| `POST` | `/api/v1/auth/login` | Inicia sesión y emite tokens. |
| `POST` | `/api/v1/auth/refresh` | Renueva el access token y rota el refresh token. |
| `POST` | `/api/v1/auth/register` | Permite iniciar el registro de usuario. |
| `GET` | `/api/v1/auth/verify-email` | Permite verificar correo mediante token. |
| `POST` | `/api/v1/auth/unlock/request` | Solicita un enlace para desbloquear una cuenta. |
| `GET` | `/api/v1/auth/unlock?token=TOKEN` | Desbloquea una cuenta mediante token. |
| `GET` | `/api/v1/stores/published` | Consulta almacenes activos publicados. |
| `OPTIONS` | `/**` | Preflight CORS. |

El registro y la verificacion de correo son publicos porque el usuario aun no tiene una cuenta activa ni un token JWT.

## Operaciones de almacenes

`POST /api/v1/stores` requiere autenticación. El servidor registra el almacén en estado `PENDING` y agrega al usuario autenticado como colaborador con rol local `OWNER`.

`PATCH /api/v1/stores/{id}` requiere `STORE_UPDATE` para ese almacén. Solo permite actualizar `businessName`, `nit` y `businessEmail`; no permite cambiar el estado ni la configuración.

`GET /api/v1/stores/{id}/status` requiere `STORE_READ`. `GET /api/v1/stores/{id}/permissions` devuelve los permisos efectivos del usuario autenticado para ese almacén. `GET /api/v1/stores/published` es público y solo devuelve almacenes en estado `ACTIVE`.

Las sucursales se administran en `/api/v1/stores/{storeId}/branches` con los permisos `BRANCH_READ`, `BRANCH_CREATE`, `BRANCH_UPDATE` y `BRANCH_DELETE`. Cada sucursal debe incluir una ubicación; su desactivación cambia el estado a `INACTIVE` y conserva el registro para el historial.

Los productos requieren `PRODUCT_READ`, `PRODUCT_CREATE`, `PRODUCT_UPDATE`, `PRODUCT_PUBLISH` o `PRODUCT_DELETE`; las imágenes requieren `PRODUCT_IMAGE_MANAGE`. Crear requiere `branchId`, fabricante, categoría y compatibilidades existentes. Precio y cantidad se guardan por sucursal en inventario. Las promociones de tienda requieren `PROMOTION_READ`, `PROMOTION_CREATE`, `PROMOTION_UPDATE` o `PROMOTION_DELETE`. El catálogo público `GET /api/v1/products/published` solo devuelve productos activos con stock en sucursales y almacenes activos.

## Registro con verificacion de correo

El registro crea la cuenta en estado `PENDING`, genera un token de proposito `EMAIL_VERIFICATION`, envia un link por correo y activa la cuenta solo cuando el token se consume correctamente.

El inicio de sesion no hace parte de este flujo.

## Bloqueo por intentos fallidos

La cuenta pasa al estado `LOCKED` cuando sus tres intentos de inicio de sesion
mas recientes fallan y provienen de la misma IP. Un inicio de sesion exitoso o
un intento desde otra IP interrumpe esa secuencia. La cuenta se bloquea despues
del tercer fallo; una cuenta bloqueada no puede iniciar sesion aunque la
contraseña sea correcta.

Para solicitar el desbloqueo:

```http
POST /api/v1/auth/unlock/request
Content-Type: application/json
```

```json
{
	"email": "ana@example.com"
}
```

La respuesta es siempre generica para no revelar si el correo existe. El enlace
recibido usa un token de un solo uso almacenado como hash en `account_tokens`.

Ver detalles en:

```text
docs/backend/auth-registration.md
```

## Passwords

Las contrasenas se validan antes de crear la cuenta y se almacenan con BCrypt.

Nunca se devuelve ni se registra en auditoria:

- contrasena en texto plano
- hash de contrasena
- token real de verificacion
- hash del token de verificacion

## Perfil propio

`GET /api/v1/auth/profile` consulta la cuenta asociada al correo del JWT. `PATCH
/api/v1/auth/profile` acepta cambios parciales solo para `name`, `surname`,
`identification`, `dateBirth` y `numberPhone`. No se recibe un identificador de
cuenta/persona: el objetivo siempre se obtiene del usuario autenticado.

Correo, contrasena, tipo, estado y configuracion de la cuenta no son editables
desde este endpoint. Los campos JSON desconocidos se rechazan y las respuestas
usan un DTO que no contiene la contrasena ni su hash. La identificacion debe ser
unica; la consulta se realiza mediante Spring Data JPA y sus parámetros, sin
construir SQL con valores del usuario.

## Tokens de verificacion

Los tokens de correo se registran en `account_tokens`.

La base de datos guarda el hash SHA-256 del token, no el token real enviado por correo.

## Recuperacion de contraseña

`POST /api/v1/auth/password-recovery/request` recibe un correo y siempre devuelve
el mismo mensaje, exista o no la cuenta. Para una cuenta existente envía un token
temporal; `account_tokens` almacena únicamente su hash SHA-256. La duración se
configura con `PASSWORD_RECOVERY_EXPIRES_MINUTES` (15 minutos por defecto).

`POST /api/v1/auth/password-recovery/reset` recibe `token` y `password`. El token
debe ser válido, no haber vencido y no haberse utilizado. Su consumo es de un solo
uso; al completar la operación, la contraseña nueva se almacena con BCrypt.
Las contraseñas deben tener entre 8 y 72 caracteres e incluir mayúscula,
minúscula, número y carácter especial. Los tokens desconocidos, vencidos o usados
son rechazados.

## Configuracion de correo

El envio de correo usa Spring Mail y variables `MAIL_*` documentadas en `.env.example`.

Para desarrollo local se puede usar un SMTP de pruebas, por ejemplo MailHog o Mailpit, ajustando:

```env
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_AUTH=false
MAIL_STARTTLS=false
```

## CORS

CORS se mantiene separado en:

```text
src/main/java/com/autopartco/backend/config/CorsConfig.java
```

`WebSecurityConfig` solo habilita la integracion con:

```java
.cors(Customizer.withDefaults())
```

## Sesiones y refresh tokens

`POST /api/v1/auth/refresh` recibe `{"refreshToken":"..."}` y devuelve el mismo
contrato de tokens que el login. Cada renovación reemplaza el refresh token
vigente de esa sesión; el token anterior deja de ser válido. La base de datos
solo conserva su hash SHA-256 hexadecimal, nunca el valor que usa el cliente.

`GET /api/v1/auth/sessions` devuelve las sesiones activas de la cuenta
autenticada, con IP, user agent, fechas y una marca `current` basada en el ID de
sesión incluido en el JWT. No devuelve refresh tokens ni sus hashes.
`DELETE /api/v1/auth/sessions/{sessionId}` revoca una sesión propia. `POST
/api/v1/auth/logout` revoca la sesión identificada por el refresh token y
`POST /api/v1/auth/logout/others` revoca las demás sesiones de esa cuenta.

Al cambiar la contraseña por recuperación se revocan todas las sesiones y sus
refresh tokens. Los access tokens JWT ya emitidos pueden seguir aceptándose
hasta su expiración, limitada por `ACCESS_TOKEN_EXPIRES_MINUTES`.

## Permisos por almacen

Las autoridades globales de cuenta (`CUSTOMER`, `STORE_MANAGER`, `ROOT`) se
mantienen en el JWT. Las acciones sobre un almacén se autorizan dinámicamente
consultando las relaciones activas
`collaborators -> roles -> roles_permissions -> permissions`; estos permisos no
se aceptan del cliente ni se copian al JWT, así que los cambios aplican
inmediatamente. `OWNER` recibe todos los permisos de tienda al registrarse y la
migración los asigna a los roles `OWNER` existentes. Otros roles reciben solo
las acciones asignadas explícitamente.

Catálogo de permisos: `STORE_READ`, `STORE_UPDATE`, `BRANCH_READ`,
`BRANCH_CREATE`, `BRANCH_UPDATE`, `BRANCH_DELETE`, `PRODUCT_READ`,
`PRODUCT_CREATE`, `PRODUCT_UPDATE`, `PRODUCT_PUBLISH`, `PRODUCT_DELETE`,
`PRODUCT_IMAGE_MANAGE`, `PROMOTION_READ`, `PROMOTION_CREATE`,
`PROMOTION_UPDATE` y `PROMOTION_DELETE`.

`GET /api/v1/stores/{storeId}/permissions` devuelve los permisos efectivos del
usuario autenticado para ese almacén. Los endpoints de tienda validan los
permisos en backend y no confían en los datos enviados por el frontend.

## Direcciones propias

Todas las operaciones usan la persona asociada al JWT; no aceptan un
`personId` proporcionado por el cliente:

| Metodo | Ruta | Operacion |
| --- | --- | --- |
| `GET` | `/api/v1/auth/addresses` | Lista las direcciones propias. |
| `POST` | `/api/v1/auth/addresses` | Crea una direccion. |
| `PUT` | `/api/v1/auth/addresses/{addressId}` | Actualiza una direccion propia. |
| `DELETE` | `/api/v1/auth/addresses/{addressId}` | Desactiva logicamente una direccion propia. |

Cada request incluye `label`, los campos de `location` y opcionalmente
`isDefault`. La primera dirección queda como predeterminada. Marcar otra como
predeterminada desmarca las demás; al eliminar la predeterminada, se elige la
dirección restante más reciente.

## Reglas para nuevos endpoints

Antes de publicar un endpoint sin autenticacion, se debe justificar por que un usuario anonimo necesita acceder a el.

Si no hay una razon clara, el endpoint debe permanecer protegido por la regla general.
