# Búsqueda de fabricantes (difusa)

`GET /api/v1/stores/{storeId}/manufacturers/search?q=` — permiso `PRODUCT_CREATE`. Devuelve hasta 8 `{id, name, approved, similarity}`; con `q` de menos de 2 caracteres devuelve `[]`. Cabecera `Cache-Control: private, max-age=30`.

`GET /api/v1/stores/{storeId}/manufacturers` devuelve la lista completa visible (aprobados + pendientes del almacén) con `ETag` / `If-None-Match` (304) y `Cache-Control: private, no-cache`.

## Cómo funciona
- Una sola consulta nativa sobre `public.immutable_unaccent(lower(name))`, la misma expresión del índice GIN `idx_manufacturers__name_trgm` (`gin_trgm_ops`, `WHERE deleted_at IS NULL`).
- Operadores `%` (similitud global) y `<%` (`word_similarity`, útil para «Bosh» contra «Robert Bosch GmbH»). Ambos usan el mismo GIN.
- Umbrales fijados por transacción con `set_config(..., true)`: `pg_trgm.similarity_threshold = 0.3` y `pg_trgm.word_similarity_threshold = 0.5`, dentro de una transacción de solo lectura.
- Orden: prefijo → contiene → `greatest(similarity, word_similarity)` descendente. Visibilidad: aprobados + pendientes sugeridos por el almacén. Los comodines `%`, `_` y `\` de `q` se escapan; `q` se trunca a 100 caracteres.

## Verificar el uso del índice (con datos reales)
```sql
BEGIN;
SET LOCAL enable_seqscan = off;
SET LOCAL pg_trgm.similarity_threshold = 0.3;
SET LOCAL pg_trgm.word_similarity_threshold = 0.5;
EXPLAIN (ANALYZE, BUFFERS)
SELECT m.id, m.name
FROM manufacturers m
WHERE m.deleted_at IS NULL
  AND (public.immutable_unaccent(lower(m.name)) % public.immutable_unaccent(lower('bosh'))
    OR public.immutable_unaccent(lower('bosh')) <% public.immutable_unaccent(lower(m.name)))
LIMIT 8;
ROLLBACK;
```
Debe mostrar `BitmapOr` con `Bitmap Index Scan on idx_manufacturers__name_trgm`. Con pocas filas el planificador puede preferir `Seq Scan` sin el `SET LOCAL enable_seqscan = off`; es normal.

## Índices recomendados (solo si el EXPLAIN de listados muestra Seq Scan)
```sql
CREATE INDEX IF NOT EXISTS idx_inventory__product_id ON inventory (product_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_product_vehicle_rules__product_id ON product_vehicle_rules (product_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_product_images__product_order ON product_images (product_id, display_order, created_at);
CREATE INDEX IF NOT EXISTS idx_products__store_id ON products (store_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_manufacturers__suggested_by_store ON manufacturers (suggested_by_store_id) WHERE deleted_at IS NULL AND approved = false;
```
