# Convenciones de API Backend - Autopartco

## Índice

- [Objetivo](#1-objetivo)
- [Prefijo de API](#2-prefijo-de-api)
- [Convenciones de nombres](#3-convenciones-de-nombres)
- [Métodos HTTP](#4-métodos-http)
- [Códigos HTTP](#5-códigos-http)
- [Formato estándar de respuestas](#6-formato-estándar-de-respuestas)
- [Formato estándar de errores](#7-formato-estándar-de-errores)
- [Paginación](#8-paginación)
- [Filtros](#9-filtros)
- [Ordenamiento](#10-ordenamiento)
- [Formato de fechas](#11-formato-de-fechas)
- [Identificadores](#12-identificadores)
- [Validación de DTO](#13-validación-de-dto)
- [Eliminación lógica](#14-eliminación-lógica)
- [Autenticación](#15-autenticación)
- [Autorización](#16-autorización)
- [Contratos independientes de la implementación](#17-contratos-independientes-de-la-implementación)
- [Ejemplo completo](#18-ejemplo-completo)
- [Checklist para nuevos endpoints](#19-checklist-para-nuevos-endpoints)
- [Criterios de aceptación del FEAT](#20-criterios-de-aceptación-del-feat)
- [Resumen de convenciones](#21-resumen-de-convenciones)

## 1. Objetivo

Establecer las convenciones técnicas que todos los integrantes del equipo deberán seguir al desarrollar y consumir endpoints del backend de Autopartco.

Estas convenciones buscan garantizar consistencia entre los diferentes módulos del sistema y permitir que los contratos de la API sean independientes de la implementación interna.

---

## 2. Prefijo de API

Todos los endpoints deberán utilizar el siguiente prefijo:

```text
/api/v1
```

Ejemplos:

```http
GET    /api/v1/products
GET    /api/v1/products/{id}
POST   /api/v1/products
PATCH  /api/v1/products/{id}
DELETE /api/v1/products/{id}
```

La versión de la API se incluye desde el inicio para permitir futuras modificaciones incompatibles sin afectar inmediatamente a los clientes que utilicen una versión anterior.

En caso de cambios incompatibles podrá utilizarse:

```text
/api/v2
```

---

## 3. Convenciones de nombres

### 3.1 URLs

Las rutas deberán:

* Utilizar letras minúsculas.
* Utilizar sustantivos para representar recursos.
* Utilizar nombres en plural para colecciones.
* Utilizar `-` para separar palabras compuestas.
* No incluir verbos que describan la operación CRUD.

Correcto:

```text
/api/v1/products
/api/v1/product-categories
/api/v1/store-branches
/api/v1/orders
```

Incorrecto:

```text
/api/v1/getProducts
/api/v1/createProduct
/api/v1/Product
/api/v1/product_categories
```

Los verbos de la operación ya están representados mediante el método HTTP.

---

### 3.2 Parámetros de ruta

Los identificadores de recursos deberán utilizar el formato:

```text
/{id}
```

Ejemplo:

```http
GET /api/v1/products/{id}
```

Para recursos relacionados:

```http
GET /api/v1/stores/{storeId}/products
```

---

### 3.3 JSON

Las propiedades de los objetos JSON deberán utilizar `camelCase`.

Correcto:

```json
{
  "id": "uuid",
  "name": "Filtro de aceite",
  "partNumber": "ABC-123",
  "createdAt": "2026-09-15T14:30:00Z"
}
```

Incorrecto:

```json
{
  "part_number": "ABC-123",
  "created_at": "2026-09-15T14:30:00Z"
}
```

La convención de nombres de la base de datos no deberá trasladarse directamente al contrato JSON de la API.

---

## 4. Métodos HTTP

| Método   | Uso                                 |
| -------- | ----------------------------------- |
| `GET`    | Consultar recursos                  |
| `POST`   | Crear recursos                      |
| `PUT`    | Reemplazar completamente un recurso |
| `PATCH`  | Actualizar parcialmente un recurso  |
| `DELETE` | Eliminar o desactivar un recurso    |

### Ejemplos

Crear un producto:

```http
POST /api/v1/products
```

Consultar un producto:

```http
GET /api/v1/products/{id}
```

Actualizar parcialmente un producto:

```http
PATCH /api/v1/products/{id}
```

Eliminar o desactivar un producto:

```http
DELETE /api/v1/products/{id}
```

---

### 4.1 Acciones de negocio

Para operaciones que representen una acción de negocio y no un CRUD directo, se podrá utilizar `POST` sobre una subruta específica.

Ejemplos:

```http
POST /api/v1/orders/{id}/cancel
POST /api/v1/orders/{id}/confirm
```

Este tipo de endpoint deberá utilizarse únicamente cuando la operación represente una transición o acción de negocio explícita.

---

## 5. Códigos HTTP

Se deberán utilizar códigos HTTP estándar.

| Código                      | Uso                                                                         |
| --------------------------- | --------------------------------------------------------------------------- |
| `200 OK`                    | Operación exitosa                                                           |
| `201 Created`               | Recurso creado correctamente                                                |
| `204 No Content`            | Operación exitosa sin contenido de respuesta                                |
| `400 Bad Request`           | Solicitud inválida                                                          |
| `401 Unauthorized`          | Usuario no autenticado                                                      |
| `403 Forbidden`             | Usuario autenticado pero sin permisos                                       |
| `404 Not Found`             | Recurso no encontrado                                                       |
| `409 Conflict`              | Conflicto con el estado actual o datos existentes                           |
| `422 Unprocessable Entity`  | Datos sintácticamente válidos pero inválidos según las reglas de validación |
| `429 Too Many Requests`     | Demasiadas solicitudes                                                      |
| `500 Internal Server Error` | Error inesperado del servidor                                               |

### Regla

No se deberá utilizar `200 OK` para representar errores.

Incorrecto:

```http
HTTP/1.1 200 OK
```

```json
{
  "success": false
}
```

Correcto:

```http
HTTP/1.1 404 Not Found
```

---

## 6. Formato estándar de respuestas

Las respuestas exitosas devuelven el recurso directamente, sin envoltorio `data`. Las listas paginadas usan el formato de la sección 8.

### 6.1 Recurso individual

```json
{
  "id": "uuid",
  "name": "Filtro de aceite",
  "price": 45000
}
```

### 6.2 Creación de un recurso

```http
HTTP/1.1 201 Created
```

```json
{
  "id": "uuid",
  "name": "Filtro de aceite",
  "price": 45000
}
```

### 6.3 Operación sin contenido

Cuando una operación sea exitosa pero no requiera devolver información:

```http
HTTP/1.1 204 No Content
```

No se deberá enviar un objeto JSON innecesario.

---

## 7. Formato estándar de errores

Todos los módulos deberán utilizar la misma estructura para representar errores:

```json
{
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "The requested product was not found.",
    "details": null
  }
}
```

### 7.1 Errores de validación

Cuando existan errores en los datos enviados por el cliente:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid data.",
    "details": {
      "name": [
        "Name is required."
      ],
      "price": [
        "Price must be greater than zero."
      ]
    }
  }
}
```

### 7.2 Código del error

La propiedad `code` deberá utilizar `UPPER_SNAKE_CASE`.

Ejemplos:

```text
VALIDATION_ERROR
UNAUTHORIZED
FORBIDDEN
PRODUCT_NOT_FOUND
STORE_NOT_FOUND
ORDER_NOT_FOUND
DUPLICATE_EMAIL
INSUFFICIENT_STOCK
INVALID_ORDER_STATUS
```

El `code` deberá ser estable y permitir que el cliente identifique el tipo de error sin depender del contenido textual de `message`.

---

## 8. Paginación

Las colecciones que puedan contener una cantidad considerable de registros deberán utilizar paginación.

Ejemplo:

```http
GET /api/v1/products?page=1&size=20
```

Respuesta (sin envoltorio `data`):

```json
{
  "items": [
    {
      "id": "uuid",
      "name": "Filtro de aceite"
    },
    {
      "id": "uuid",
      "name": "Pastillas de freno"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 150,
  "totalPages": 8
}
```

- `totalPages = ceil(total / size)`; `0` si `total = 0`.
- Una `page` mayor que `totalPages` devuelve `items: []` con `total` correcto.
- Backend: `PageResponse<T>(List<T> items, int page, int size, long total, int totalPages)`.

### 8.1 Parámetros

| Parámetro | Descripción                      | Valor predeterminado |
| --------- | -------------------------------- | -------------------: |
| `page`    | Número de página, desde `1`      |                  `1` |
| `size`    | Cantidad de elementos por página |                 `20` |

Cada endpoint fija su `size` por defecto y máximo; el máximo recomendado es:

```text
size <= 100
```

Valores fuera de rango responden `400 VALIDATION_ERROR` con `details` por campo.

---

## 9. Filtros

Los filtros deberán enviarse mediante query parameters.

Ejemplo:

```http
GET /api/v1/products?brandId=uuid&categoryId=uuid&available=true
```

### 9.1 Búsqueda por texto

Para búsquedas generales se utilizará:

```text
search
```

Ejemplo:

```http
GET /api/v1/products?search=filtro
```

### 9.2 Múltiples filtros

Los filtros podrán combinarse:

```http
GET /api/v1/products?search=freno&brandId=uuid&available=true&page=1&size=20
```

### 9.3 Rangos numéricos

Para rangos se utilizarán los prefijos `min` y `max`.

Ejemplo:

```http
GET /api/v1/products?minPrice=20000&maxPrice=100000
```

---

## 10. Ordenamiento

Para ordenar resultados se utilizarán:

```text
sortBy
sortOrder
```

Ejemplo:

```http
GET /api/v1/products?sortBy=price&sortOrder=asc
```

Valores permitidos para `sortOrder`:

```text
asc
desc
```

Ejemplo:

```http
GET /api/v1/products?sortBy=price&sortOrder=desc
```

El backend deberá mantener una lista de campos permitidos para `sortBy`.

No se deberá permitir que el cliente envíe directamente nombres arbitrarios de columnas de la base de datos.

Ejemplo de campos permitidos:

```text
name
price
createdAt
```

---

## 11. Formato de fechas

Todas las fechas intercambiadas mediante la API deberán utilizar el estándar ISO 8601.

### 11.1 Fecha y hora

```text
2026-09-15T14:30:00Z
```

### 11.2 Fecha sin hora

```text
2026-09-15
```

Ejemplo:

```json
{
  "createdAt": "2026-09-15T14:30:00Z",
  "updatedAt": "2026-09-15T15:45:00Z"
}
```

No se deberán utilizar formatos ambiguos como:

```text
15/09/2026
09-15-2026
15-09-2026 14:30
```

---

## 12. Identificadores

Autopartco utilizará **UUID** como identificador de las entidades principales.

Ejemplo:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Reglas

* Los identificadores deberán utilizar UUID.
* Los UUID se deberán representar como `string` en JSON.
* El backend o la base de datos deberá generar los identificadores.
* El frontend no deberá generar IDs para recursos nuevos salvo que un caso específico lo requiera.
* No se deberán exponer identificadores internos innecesarios.

El esquema actual de PostgreSQL utiliza UUID para los identificadores de las principales entidades del sistema.

---

## 13. Validación de DTO

Toda información recibida desde el cliente deberá validarse mediante DTOs.

Ejemplos:

```text
CreateProductDto
UpdateProductDto
CreateOrderDto
UpdateOrderStatusDto
LoginDto
RegisterDto
```

Los DTO deberán definir:

* Campos permitidos.
* Tipos de datos.
* Campos obligatorios.
* Longitud máxima y mínima.
* Rangos permitidos.
* Formatos.
* Restricciones básicas de entrada.

### Ejemplo

Request:

```json
{
  "name": "Filtro de aceite",
  "price": 45000,
  "stock": 10
}
```

Request inválido:

```json
{
  "name": "",
  "price": -500,
  "stock": -10
}
```

Respuesta:

```http
422 Unprocessable Entity
```

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid data.",
    "details": {
      "name": [
        "Name is required."
      ],
      "price": [
        "Price must be greater than zero."
      ],
      "stock": [
        "Stock cannot be negative."
      ]
    }
  }
}
```

La validación de entrada deberá realizarse antes de ejecutar la lógica de negocio.

---

## 14. Eliminación lógica

Cuando una entidad implemente eliminación lógica, el endpoint podrá utilizar:

```http
DELETE /api/v1/products/{id}
```

La eliminación deberá establecer la fecha de eliminación lógica:

```text
deletedAt = current timestamp
```

en lugar de eliminar físicamente el registro.

Las consultas normales deberán excluir los registros eliminados lógicamente.

Esta estrategia deberá utilizarse cuando la conservación del historial sea necesaria.

---

## 15. Autenticación

Los endpoints privados deberán requerir autenticación mediante token.

Ejemplo:

```http
Authorization: Bearer <access-token>
```

El backend deberá verificar:

1. Que el token sea válido.
2. Que el usuario esté autenticado.
3. Que la cuenta se encuentre activa.
4. Que el usuario tenga los permisos requeridos.
5. Que el usuario tenga acceso al recurso solicitado.

---

## 16. Autorización

La autenticación y autorización son responsabilidades diferentes.

### Autenticación

Determina:

> ¿Quién es el usuario?

### Autorización

Determina:

> ¿Qué puede hacer el usuario?

Ejemplo:

```text
GET /api/v1/products
```

podría estar disponible para usuarios autenticados o públicos.

Mientras que:

```text
POST /api/v1/products
```

podría requerir permisos específicos de administración de productos.

Los permisos deberán determinarse según los roles y reglas de negocio definidos para cada módulo.

---

## 17. Contratos independientes de la implementación

Los consumidores de la API deberán depender únicamente del contrato HTTP.

El contrato deberá definir:

```text
Método HTTP
Ruta
Parámetros
Headers
Request Body
Response Body
Códigos HTTP
Formato de errores
Requisitos de autenticación
```

El consumidor no deberá necesitar conocer:

```text
Repository
Service
Entity
ORM
SQL
Estructura interna del módulo
```

Los cambios internos de implementación no deberán modificar el contrato público mientras no exista una razón para cambiar la versión de la API.

---

## 18. Ejemplo completo

### Request

```http
GET /api/v1/products?search=freno&minPrice=30000&maxPrice=150000&sortBy=price&sortOrder=asc&page=1&size=10
```

### Response exitosa

```http
200 OK
```

```json
{
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Pastillas de freno delanteras",
      "partNumber": "PF-001",
      "price": 85000,
      "available": true
    }
  ],
  "page": 1,
  "size": 10,
  "total": 1,
  "totalPages": 1
}
```

### Response con error

```http
404 Not Found
```

```json
{
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "The requested product was not found.",
    "details": null
  }
}
```

---

## 19. Checklist para nuevos endpoints

Antes de realizar un Pull Request, el desarrollador deberá verificar:

* [ ] El endpoint utiliza `/api/v1`.
* [ ] La URL utiliza nombres en minúsculas.
* [ ] Las colecciones utilizan nombres en plural.
* [ ] La URL no utiliza verbos CRUD.
* [ ] Se utiliza el método HTTP correspondiente.
* [ ] Los identificadores utilizan UUID.
* [ ] Los JSON utilizan `camelCase`.
* [ ] Las fechas utilizan ISO 8601.
* [ ] Los DTO están definidos.
* [ ] Los DTO validan los datos recibidos.
* [ ] Se utilizan códigos HTTP apropiados.
* [ ] Los errores utilizan el formato estándar.
* [ ] Las listas utilizan paginación cuando corresponde.
* [ ] Los filtros utilizan query parameters.
* [ ] El ordenamiento utiliza `sortBy` y `sortOrder`.
* [ ] `sortBy` solo permite campos autorizados.
* [ ] Se verifica autenticación cuando corresponde.
* [ ] Se verifica autorización cuando corresponde.
* [ ] Se respeta la eliminación lógica cuando corresponda.
* [ ] El contrato no depende de la implementación interna.
* [ ] El endpoint está documentado.

---

## 20. Criterios de aceptación del FEAT

Este estándar se considerará implementado cuando:

* [x] Existe documentación de las convenciones de API.
* [x] Todos los integrantes pueden desarrollar endpoints siguiendo las mismas reglas.
* [x] Está definido un formato común para errores.
* [x] Está definido cómo se representan las listas paginadas.
* [x] Están definidos los códigos HTTP para errores comunes.
* [x] Los contratos de API no dependen de la implementación interna de otro módulo.

---

## 21. Resumen de convenciones

| Elemento           | Convención                            |
| ------------------ | ------------------------------------- |
| Prefijo            | `/api/v1`                             |
| URLs               | `kebab-case`, minúsculas, sustantivos |
| Colecciones        | Plural                                |
| JSON               | `camelCase`                           |
| IDs                | UUID                                  |
| Crear              | `POST`                                |
| Consultar          | `GET`                                 |
| Reemplazar         | `PUT`                                 |
| Actualizar         | `PATCH`                               |
| Eliminar           | `DELETE`                              |
| Respuesta          | Recurso directo, sin envoltorio `data` |
| Error              | `{ "error": ... }`                    |
| Paginación         | `page` + `size` → `{ items, page, size, total, totalPages }` |
| Filtros            | Query parameters                      |
| Ordenamiento       | `sortBy` + `sortOrder`                |
| Fechas             | ISO 8601                              |
| Validación         | DTO                                   |
| Autenticación      | Bearer Token                          |
| Versionamiento     | `/api/v1`, `/api/v2`, etc.            |
| Eliminación lógica | `deletedAt` cuando corresponda        |
