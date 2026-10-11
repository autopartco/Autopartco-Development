# Registro y verificacion de correo

## Índice

- [Flujo](#flujo)
- [Endpoint de registro](#endpoint-de-registro)
- [Endpoint de verificación](#endpoint-de-verificacion)
- [Tokens de cuenta](#tokens-de-cuenta)
- [Auditoría](#auditoria)
- [Variables de entorno](#variables-de-entorno)

## Flujo

El registro de usuarios no activa la cuenta inmediatamente.

1. El cliente envia `POST /api/v1/auth/register`.
2. El backend valida datos obligatorios, correo unico y formato de contrasena.
3. Se crea `persons`.
4. Se crea `accounts` con `CUSTOMER`, estado `PENDING` y password BCrypt.
5. Se genera un token de proposito `EMAIL_VERIFICATION`.
6. Se guarda en `account_tokens` el hash SHA-256 del token, su expiracion y `used = false`.
7. Se envia un correo con un link al frontend: `GET {APP_FRONTEND_BASE_URL}/verify-email?token=...`.
8. El frontend consume `GET /api/v1/auth/verify-email?token=...`; el backend valida el token y cambia la cuenta a `ACTIVE`.

El inicio de sesión ocurre en `POST /api/v1/auth/login` después de activar la
cuenta. El cliente recibe un access token JWT y un refresh token. El manejo de
sesiones se describe en `docs/backend/security.md`.

## Endpoint de registro

```http
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "name": "Ana",
  "surname": "Perez",
  "identification": "1234567890",
  "dateBirth": "1995-04-20",
  "numberPhone": "3001234567",
  "email": "ana@example.com",
  "password": "Password123!"
}
```

Respuesta exitosa:

```json
{
  "accountId": "uuid",
  "personId": "uuid",
  "email": "ana@example.com",
  "accountType": "CUSTOMER",
  "state": "PENDING",
  "verificationEmailSent": true,
  "verificationTokenExpiresAt": "2026-09-19T03:45:00"
}
```

La respuesta nunca incluye la contrasena, el hash de la contrasena, el token ni el hash del token.

## Endpoint de verificacion

```http
GET /api/v1/auth/verify-email?token=TOKEN_DEL_CORREO
```

Respuesta exitosa:

```json
{
  "accountId": "uuid",
  "email": "ana@example.com",
  "state": "ACTIVE",
  "message": "Correo verificado correctamente."
}
```

## Tokens de cuenta

Los tokens se guardan en `account_tokens`.

| Campo | Uso |
| --- | --- |
| `account_id` | Cuenta asociada al token. |
| `purpose` | Proposito del token. Para este flujo: `EMAIL_VERIFICATION`. |
| `token` | Hash SHA-256 Base64 URL-safe del token enviado por correo. |
| `expiration_at` | Fecha limite para verificar. |
| `used` | Indica si el token ya fue consumido. |

El valor real enviado por correo no se guarda en base de datos.

## Auditoria

El flujo registra eventos en `audit_logs`:

| Accion | Momento |
| --- | --- |
| `USER_REGISTRATION_REQUESTED` | Despues de crear la cuenta pendiente. |
| `USER_EMAIL_VERIFIED` | Despues de activar la cuenta por link. |

No se debe guardar la contrasena, el hash de la contrasena, el token real ni el hash del token en auditoria.

## Variables de entorno

`spring.jpa.hibernate.ddl-auto=update` está activo en la configuración base para
que Hibernate sincronice el esquema al iniciar. `update` no transforma ni migra
datos existentes: antes de usar una base con sesiones anteriores, aplica
`database/migrations/005_auth_security_and_permissions.sql`, que migra
`refresh_token` a `refresh_token_hash` y conserva las sesiones hasheando sus
tokens. Revisa las migraciones SQL antes de usar cambios de esquema en producción.

```env
APP_PUBLIC_BASE_URL=http://localhost:8081
EMAIL_VERIFICATION_EXPIRES_MINUTES=30

MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=correo@example.com
MAIL_PASSWORD=tu_password_de_aplicacion
MAIL_FROM=no-reply@autopartco.com
MAIL_AUTH=true
MAIL_STARTTLS=true
```

`APP_PUBLIC_BASE_URL` apunta al backend y se usa para enlaces de recuperación/desbloqueo. `APP_FRONTEND_BASE_URL` apunta al frontend y se usa para el enlace de verificacion de correo. En producción se pueden configurar, por ejemplo, como `https://api.autopartco.co` y `https://autopartco.co`, respectivamente.

El frontend llama al API usando `VITE_API_BASE_URL` (incluyendo `/api/v1`). Para desarrollo puede dejarse sin configurar y utilizar el proxy local; en producción, por ejemplo, se configura como `https://api.autopartco.co/api/v1`.
