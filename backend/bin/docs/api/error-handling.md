# Manejo centralizado de errores de API

## Índice

- [Objetivo](#1-objetivo)
- [Formato estándar de errores](#2-formato-estándar-de-errores)
- [Códigos HTTP](#3-códigos-http)
- [Errores de validación](#4-errores-de-validación--400)
- [Errores de autenticación](#5-errores-de-autenticación--401)
- [Errores de autorización](#6-errores-de-autorización--403)
- [Recursos inexistentes](#7-recursos-inexistentes--404)
- [Conflictos de datos](#8-conflictos-de-datos--409)
- [Errores inesperados](#9-errores-inesperados--500)
- [Información sensible](#10-información-sensible)
- [Manejo centralizado](#11-manejo-centralizado)
- [Reglas generales](#12-reglas-generales)
- [Criterios de aceptación](#13-criterios-de-aceptación)

## 1. Objetivo

Establecer una convención común para el manejo de errores generados por la API de Autopartco, utilizando un formato JSON uniforme y códigos HTTP consistentes.

El manejo de errores debe estar centralizado para evitar respuestas diferentes entre los distintos módulos del backend.

---

## 2. Formato estándar de errores

Todas las respuestas de error deberán utilizar la siguiente estructura:

```json
{
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "El producto solicitado no existe.",
    "details": null
  }
}
```

### Campos

| Campo           | Descripción                                        |
| --------------- | -------------------------------------------------- |
| `error.code`    | Código identificador del tipo de error.            |
| `error.message` | Mensaje descriptivo para el cliente.               |
| `error.details` | Información adicional del error. Puede ser `null`. |

Los códigos de error deberán utilizar `UPPER_SNAKE_CASE`.

Ejemplos:

* `VALIDATION_ERROR`
* `UNAUTHORIZED`
* `FORBIDDEN`
* `PRODUCT_NOT_FOUND`
* `EMAIL_ALREADY_EXISTS`
* `INTERNAL_SERVER_ERROR`

---

## 3. Códigos HTTP

| Código | Nombre                | Uso                                                                            |
| ------ | --------------------- | ------------------------------------------------------------------------------ |
| `400`  | Bad Request           | Solicitud inválida o errores de validación.                                    |
| `401`  | Unauthorized          | El usuario no está autenticado o las credenciales no son válidas.              |
| `403`  | Forbidden             | El usuario está autenticado pero no tiene permisos para realizar la operación. |
| `404`  | Not Found             | El recurso solicitado no existe.                                               |
| `409`  | Conflict              | Existe un conflicto con los datos o el estado actual del recurso.              |
| `500`  | Internal Server Error | Error inesperado del servidor.                                                 |

---

## 4. Errores de validación — 400

Los errores relacionados con datos inválidos enviados por el cliente deberán retornar `400 Bad Request`.

Ejemplo:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Los datos enviados no son válidos.",
    "details": {
      "name": "El nombre es obligatorio.",
      "price": "El precio debe ser mayor que cero."
    }
  }
}
```

---

## 5. Errores de autenticación — 401

Cuando una operación requiera autenticación y el usuario no esté autenticado, se deberá retornar `401 Unauthorized`.

Ejemplo:

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Se requiere autenticación para realizar esta operación.",
    "details": null
  }
}
```

No se deberán revelar contraseñas, tokens ni información sensible relacionada con las credenciales.

---

## 6. Errores de autorización — 403

Cuando el usuario esté autenticado pero no tenga los permisos necesarios, se deberá retornar `403 Forbidden`.

Ejemplo:

```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "No tiene permisos para realizar esta operación.",
    "details": null
  }
}
```

---

## 7. Recursos inexistentes — 404

Cuando el recurso solicitado no exista, se deberá retornar `404 Not Found`.

Ejemplo:

```json
{
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "El producto solicitado no existe.",
    "details": null
  }
}
```

---

## 8. Conflictos de datos — 409

Cuando una operación genere un conflicto con los datos existentes o con el estado actual de un recurso, se deberá retornar `409 Conflict`.

Ejemplo:

```json
{
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "El correo electrónico ya se encuentra registrado.",
    "details": null
  }
}
```

Otros casos pueden incluir:

* Registro duplicado.
* SKU ya existente.
* Conflicto con el estado actual de una orden.
* Operación incompatible con el estado actual de un recurso.

---

## 9. Errores inesperados — 500

Los errores inesperados deberán retornar `500 Internal Server Error`.

La respuesta al cliente deberá utilizar un mensaje genérico:

```json
{
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "message": "Ha ocurrido un error interno en el servidor.",
    "details": null
  }
}
```

Los detalles técnicos del error deberán registrarse únicamente en los logs del backend.

---

## 10. Información sensible

Las respuestas de error **no deberán exponer información sensible o interna**.

No se deberá enviar al cliente:

* Contraseñas.
* Tokens de autenticación.
* Credenciales.
* Stack traces.
* Consultas SQL.
* Información de conexión a la base de datos.
* Rutas internas del servidor.
* Variables de entorno.
* Información sensible de otros usuarios.

Ejemplo incorrecto:

```json
{
  "error": {
    "message": "duplicate key value violates unique constraint users_email_key"
  }
}
```

Ejemplo correcto:

```json
{
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "El correo electrónico ya se encuentra registrado.",
    "details": null
  }
}
```

---

## 11. Manejo centralizado

El backend deberá contar con un mecanismo centralizado para capturar las excepciones y transformarlas en respuestas HTTP siguiendo este contrato.

Conceptualmente:

```text
Excepción
    │
    ├── Validación ──────────► 400
    ├── No autenticado ─────► 401
    ├── Sin permisos ────────► 403
    ├── No encontrado ──────► 404
    ├── Conflicto ──────────► 409
    └── Inesperado ─────────► 500
```

Los diferentes módulos deberán utilizar este mecanismo en lugar de definir formatos de error propios.

---

## 12. Reglas generales

1. Todas las respuestas de error deben utilizar el formato JSON definido en este documento.
2. Los errores de validación deben retornar `400`.
3. Los recursos inexistentes deben retornar `404`.
4. Los conflictos de datos deben retornar `409`.
5. Las operaciones no autorizadas deben retornar `403`.
6. Las solicitudes sin autenticación válida deben retornar `401`.
7. Los errores inesperados deben retornar `500`.
8. No se deben mostrar stack traces al cliente.
9. No se deben exponer contraseñas, tokens ni información sensible.
10. Los errores técnicos deben registrarse internamente mediante logs.
11. Los módulos no deben definir formatos de error diferentes al establecido.

---

## 13. Criterios de aceptación

* [x] Los errores tienen un formato JSON uniforme.
* [x] Los errores de validación retornan `400`.
* [x] Los recursos inexistentes retornan `404`.
* [x] Los conflictos de datos retornan `409`.
* [x] Las operaciones no autorizadas retornan `403`.
* [x] Las solicitudes no autenticadas retornan `401`.
* [x] Los errores inesperados retornan `500`.
* [x] No se muestran stack traces al cliente.
* [x] No se exponen contraseñas, tokens ni información sensible.
* [x] El manejo de errores está definido de manera centralizada.
