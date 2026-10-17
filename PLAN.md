# Plan: registro de productos (v2) — wizard, reglas de vehículos, envío atómico, Openinary

Fecha: 2026-10-17. Estado: propuesto. Secuencia obligatoria: `database` → `backend` → `frontend` → `qa-seguridad`.

## 1. Objetivo y alcance
Entra:
1. Requisitos para publicar (imagen, vehículos, fabricante válido, suscripción activa; stock 0 = aviso, no bloqueo).
2. Formulario por secciones (wizard) con avisos/estados, precio en COP y ayudas.
3. Marca visible en el marketplace aunque el fabricante esté pendiente (la aprobación solo lo lista como opción en otros almacenes).
4. Compatibilidad como **reglas** Marca→Modelo→Versión con «todos» (modelo/versión opcional), varias reglas por producto, validadas por coherencia con el catálogo.
5. **Nada va al servidor hasta pulsar el botón final** (crear / guardar / publicar): datos, imágenes y fabricante nuevo en un solo multipart atómico. Auditoría y migración de todos los formularios del sistema.
6. Performance: búsqueda difusa, caché/HTTP, imágenes con Openinary (+ compresión en cliente).
No entra: cola de moderación de fabricantes (después); producción de Openinary (solo Docker local + capa abstracta); edición del catálogo maestro de vehículos por vendedores.

## 2. Componentes y responsabilidades
- **BD:** tabla `product_vehicle_rules` (+ migración desde `product_compatibilities`), índices de matching.
- **Backend:** catálogo de vehículos en cascada; reglas; creación/edición atómica multipart; requisitos de publicación; `manufacturerName` en respuestas; búsqueda difusa; `ImageUrls` (Openinary); cabeceras de caché.
- **Frontend:** patrón «borrador local» reutilizable; wizard; selector de reglas; combobox de fabricante diferido; imágenes locales; consumo de variantes Openinary; migración de formularios.
- **Infra:** `docker-compose.openinary.yml` (solo dev) y variable `OPENINARY_URL`.

## 3. Contratos
**Catálogo de vehículos (público, `Cache-Control: public, max-age=3600` + ETag):**
- `GET /api/v1/marketplace/vehicles/makes` → `[{id,name}]`
- `GET /api/v1/marketplace/vehicles/makes/{makeId}/models` → `[{id,name}]`
- `GET /api/v1/marketplace/vehicles/models/{modelId}/versions` → `[{id,label}]`

**Regla de vehículo:** `{makeId: uuid, modelId?: uuid, versionId?: uuid}` — `versionId` exige `modelId`; ausente = «todos». Sin duplicados ni reglas contenidas en otra (el servidor normaliza). Coherencia: modelo∈marca, versión∈modelo; si no, 400 `VEHICLE_RULE_INVALID` con `details[i]`.

**Crear (atómico):** `POST /stores/{storeId}/products` `multipart/form-data`
- `data` (application/json): `{branchId,name,sku?,partNumber?,description,price,quantity,categoryId,manufacturerId? | newManufacturerName?, confirmNewManufacturer?, vehicleRules:[Regla], attributes?, mainImageIndex?, publish:boolean}`
- `images` (0..10 archivos JPEG/PNG/WebP ≤5 MB, orden = orden de galería).
- Exactamente uno de `manufacturerId` / `newManufacturerName`. Nuevo nombre con parecidos y sin `confirmNewManufacturer:true` → 409 `MANUFACTURER_SIMILAR` (details `[{id,name}]`); tope 20 pendientes por almacén.
- `publish:true` exige requisitos; si faltan → 409 `PRODUCT_NOT_PUBLISHABLE` con `details:[{code:'NO_IMAGES'|'NO_VEHICLES'|'MANUFACTURER_INVALID'|'SUBSCRIPTION_INACTIVE', message}]` y **no se crea nada**. Stock 0 no bloquea.
- Todo o nada: si falla Storage/BD se revierte y se limpian archivos subidos. Respuesta 201 `ProductResponse` (con `images`, `vehicleRules`, `manufacturerName`, `manufacturerApproved`).

**Editar (atómico):** `PATCH /stores/{storeId}/branches/{branchId}/products/{productId}` multipart: `data` (campos opcionales, `vehicleRules` reemplaza el conjunto, `publish?`) + `newImages` + `data.removeImageIds[]`, `data.imageOrder[]` (ids existentes y `new:<i>`), `data.mainImage` (id o `new:<i>`).

**Respuestas públicas y de almacén:** `ProductResponse` añade `manufacturerName` (también si está pendiente, sin otros datos) y `vehicleRules` (con nombres). Las listas de fabricantes siguen igual: solo aprobados + pendientes propios.

**Filtro por vehículo (marketplace):** `makeId`, `modelId`, `versionId` → coincide con reglas que cubren ese vehículo (regla con modelo/versión nulos cubre todo bajo ella).

**Búsqueda de fabricantes:** `GET /stores/{id}/manufacturers/search?q=` una sola consulta con `%` (umbral vía `set_config('pg_trgm.similarity_threshold', …, true)`) + `similarity()` + prefijo; GIN existente; `Cache-Control: private, max-age=30`. `GET /stores/{id}/manufacturers` con ETag.

**Imágenes:** `ImageUrls.variant(storagePath, {w,h,fit,format})` en backend; si `OPENINARY_URL` está definido, `url` y `thumbUrl` apuntan a Openinary (sintaxis de URL de transformación según docs.openinary.dev, verificar al implementar); si no, URL pública de Supabase. Frontend: `imageVariant(url,{w,h})`.

## 4. Decisiones
- Reglas con nulos = «todos» en lugar de expandir: cubre modelos nuevos sin tocar productos. Descartado: expandir a combinaciones.
- Multipart único: evita productos sin imágenes por fallo parcial. Descartado: crear y luego subir.
- Fabricante nuevo viaja con el producto; la similitud se consulta en el cliente con la búsqueda de solo lectura y se confirma localmente.
- Las lecturas (búsqueda, catálogo) pueden ir al servidor mientras se llena el formulario; las escrituras, no.
- Migración expand/contract: `product_compatibilities` se conserva hasta validar y se elimina en una V posterior.

## 5. Tareas (cada una ejecutable en chat nuevo)
**T1 · `database` · Sonnet** — `database/migrations/V2026101710xx__product_vehicle_rules.sql` (+U). Crea `product_vehicle_rules(id, product_id→products, make_id→make, model_id NULL, version_id NULL, created_at, deleted_at)`; CHECK `version_id IS NULL OR model_id IS NOT NULL`; FKs compuestas `(model_id,make_id)→model(id,make_id)` y `(version_id,model_id)→versions(id,model_id)`; único sobre `(product_id, make_id, coalesce(model_id,zero-uuid), coalesce(version_id,zero-uuid))`; índice `(make_id, model_id, version_id)`; migra `product_compatibilities` a reglas exactas. Contexto: `database/init.sql` ~l.465-580, README de migraciones. Aceptación: idempotente, `EXPLAIN` del match por vehículo usa el índice, conteo de reglas = conteo de compatibilidades migradas (consulta documentada). No ejecutar en BD sin autorización.

**T2 · `backend` · Sonnet** — Catálogo de vehículos en cascada (3 endpoints, caché/ETag) + `ProductOptionsResponse` sin lista gigante de compatibilidades. Archivos: `core/vehicle/*`, `marketplace/catalog/*`. Aceptación: pruebas de controlador + cabeceras de caché.

**T3 · `backend` · Opus** — Reglas + creación/edición atómica multipart + requisitos de publicación + `manufacturerName`/`manufacturerApproved`. Archivos: `core/catalog/*`, `storesuite/products/*`, `ListingQueries`, `PublishProduct`. Aceptación: pruebas con `-Dtest=` de: coherencia de reglas (BMW+Mustang → 400), «todos» expandido en el filtro, `PRODUCT_NOT_PUBLISHABLE` sin efectos, rollback y limpieza de Storage ante fallo, fabricante nuevo con parecido → 409, IDOR entre almacenes, `publish` en la misma petición, marca visible con fabricante pendiente. Retirar el uso de `compatibilityIds`.

**T4 · `backend` · Sonnet** — Performance: búsqueda difusa de una consulta con umbral por transacción; ETag/Cache en listas; `ImageUrls` + `OPENINARY_URL` + `docker-compose.openinary.yml` (dev, bucket S3 de Supabase solo lectura; sin secretos en el repo). Aceptación: `EXPLAIN (ANALYZE)` documentado en `docs/backend/` mostrando uso del GIN; prueba de `ImageUrls` con y sin Openinary.

**T5 · `frontend` · Sonnet** — Patrón «borrador local» (`useDraftForm`, guardia de cambios sin guardar, `useSubmitOnce`) en `frontend/src/lib/` y componentes `FormSection`/`Stepper`. Documentar en `docs/frontend/`. Aceptación: prueba que verifica cero llamadas de escritura antes de `submit`.

**T6 · `frontend` · Sonnet** — Selector de reglas de vehículo (Marca→Modelo→Versión, «Todos», varias marcas, chips resumen) usando los endpoints de T2; reemplaza `compatibility-picker.tsx`. Aceptación: no permite combinación incoherente (opciones dependientes), carga perezosa por nivel, accesible por teclado.

**T7 · `frontend` · Sonnet** — Wizard de producto en `product-form-page.tsx`: pasos Producto → Vehículos → Precio y stock → Imágenes → Revisión; panel «Qué falta para publicar» (checklist y estados), precio en COP formateado (`Intl.NumberFormat('es-CO')`), ayudas por campo, botones «Guardar borrador» y «Crear y publicar»; imágenes locales con previsualización, compresión a WebP ≤1600 px, reordenar y principal; combobox de fabricante diferido (sin POST). Un único `multipart` al final con los contratos de T3. Aceptación: pestaña Red sin escrituras hasta el botón; error 409 `PRODUCT_NOT_PUBLISHABLE`/`MANUFACTURER_SIMILAR` se muestra en el paso correcto; móvil y tema oscuro.

**T8 · `frontend` · Sonnet** — Marketplace: marca por `manufacturerName`, filtro por vehículo con los endpoints de T2, variantes Openinary en tarjetas y detalle. Aceptación: tarjeta muestra marca de fabricante pendiente; imágenes pequeñas en listados.

**T9 · `frontend` · Haiku/Sonnet** — Auditoría de formularios (lista: auth ×3, seller ×2, checkout address, portal sucursales/equipo/invitar/perfil/inventario/pedidos/envío, admin revisión, solicitud de cotización). Regla: ninguna escritura antes del envío; lecturas de apoyo permitidas. Entregar `docs/frontend/forms-audit.md` y migrar los que no cumplan al patrón de T5. Aceptación: tabla formulario → cumple/migrado, con evidencia.

**T10 · `qa-seguridad` · Sonnet (Opus si hay hallazgos críticos)** — Revisión: IDOR, multipart (tamaño, MIME real, número de archivos), rollback de Storage, inyección en reglas, XSS de URLs de imagen, filtro por vehículo, abuso del fabricante nuevo. Aceptación: informe con severidades y pruebas faltantes.

## 6. Riesgos / verificar
- Sintaxis exacta de URLs de Openinary y su soporte de bucket S3 de Supabase (verificar en docs antes de T4).
- Migración de datos de compatibilidades y consultas del marketplace que dependen de `compatibilityIds`.
- Multipart grande detrás de proxy/límites de Spring (`max-file-size`, `max-request-size`) y de Supabase.
- La migración V de fabricantes (`V202610161000`) y el seed ya están aplicados en la BD del `.env` (perfil `prod`); toda migración nueva requiere autorización antes de aplicarla.
- Cambios de contrato rompen el cliente actual: T3 y T7 deben desplegarse juntos.
- T9 es amplio; si crece, dividir por feature.
