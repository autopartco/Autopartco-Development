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
7. Se envia un correo con un link a `GET /api/v1/auth/verify-email?token=...`.
8. Cuando el usuario abre el link, el backend valida el token y cambia la cuenta a `ACTIVE`.

No se implementa inicio de sesion en este flujo.

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

```env
APP_PUBLIC_BASE_URL=http://localhost:8080
EMAIL_VERIFICATION_EXPIRES_MINUTES=30

MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=correo@example.com
MAIL_PASSWORD=tu_password_de_aplicacion
MAIL_FROM=no-reply@autopartco.com
MAIL_AUTH=true
MAIL_STARTTLS=true
```

`APP_PUBLIC_BASE_URL` debe apuntar al backend cuando el link de verificacion sea consumido directamente por la API.
