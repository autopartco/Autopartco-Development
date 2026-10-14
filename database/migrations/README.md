# Migraciones de base de datos de Autopartco (PostgreSQL 16+)

Este directorio contiene las migraciones versionadas del esquema. Siguen la convención de Flyway del ADR 0001 (`docs/adr/0001-estrategia-de-esquema-y-migraciones.md`) y el diseño predictivo (`docs/architecture/2026-10-13-database-predictive-data-model.md`).

- **Línea base:** `database/init.sql`, que no se modifica. Las migraciones parten de un esquema creado con ese archivo. `V202610130900` aborta si no lo encuentra.
- **Alcance:** solo Colombia: COP, DIVIPOLA, festivos por la Ley 51 de 1983 y Ley 1581 de 2012. Las tablas con `country_code` usan `DEFAULT 'CO'`.
- **Nada de esto se ha ejecutado** contra ninguna base de datos. Los comandos de este documento los ejecuta el usuario.

## 1. Convenciones

| Archivo | Significado |
|---|---|
| `V<AAAAMMDDHHMM>__desc.sql` | Migración versionada (se aplica una vez, en orden numérico) |
| `U<mismo>__desc.sql` | Rollback manual de esa `V`. Flyway Community no ejecuta los `U` |
| `R__desc.sql` | Repetible: semillas de referencia. Flyway la reaplica si cambia su checksum, siempre después de las `V` |
| `<archivo>.sql.conf` | Configuración del script en Flyway. Aquí siempre es `executeInTransaction=false` |
| `pending-approval/PA_*.sql` | **Requiere aprobación.** El prefijo `PA_` impide que Flyway las detecte (ver §4) |

- Nombres de objetos nuevos: `pk_`, `fk_<tabla>__<col>`, `uq_`, `chk_<tabla>__<regla>`, `idx_`, `brin_` y `trg_<tabla>_...`.
- Todo tiempo nuevo es `TIMESTAMPTZ`. Las columnas `TIMESTAMP` heredadas se leen con `public.assume_utc()` y `public.bogota_date()`.
- Los archivos son idempotentes (`IF NOT EXISTS`, `CREATE OR REPLACE`, comprobaciones en `pg_constraint`) por petición explícita de la tarea. **Esto contradice la regla 7 del ADR 0001** ("no usar IF NOT EXISTS en versionadas"). Ver §8.
- Cada `U` que perdería datos empieza con un guard: solo se ejecuta si la sesión fija `SET autopartco.allow_destructive = 'on'`.

## 2. Orden de aplicación

### Fase de optimización e integridad del esquema existente

| Versión | Tx | Qué resuelve |
|---|---|---|
| `V202610130900__extensions_and_helpers` | sí | Marcador de línea base. Extensiones `pg_trgm` y `unaccent`. `set_updated_at()` y `immutable_unaccent()` |
| `V202610130905__entity_drift_fixes` | sí | `seller_plans.max_branches/max_products/max_users/capabilities` con relleno igual a `PlanService`. `persons.number_phone` pasa a VARCHAR(20). Se quita `inventory.price DEFAULT 0` |
| `V202610130910__missing_indexes` | **no** | 57 índices (`CONCURRENTLY`). Incluye los críticos `lower/upper(email)`, `products(store_id,name)`, `account_sessions` y `products_compatibilies(compatibility_id)`, los compuestos de consultas reales y los de las FK sin índice |
| `V202610130915__drop_redundant_indexes` | **no** | Quita 10 índices redundantes tras verificar que los cubre otro índice. Conserva `idx_sales_branch` |
| `V202610130920__check_constraints_not_valid` | sí | 40 CHECK `NOT VALID` (enumeraciones y reglas de negocio contrastadas con el backend) |
| `V202610130921__validate_check_constraints` | sí | Diagnóstico de filas que incumplen y `VALIDATE` |
| `V202610130925__tenant_isolation_triggers` | sí | Aislamiento por almacén con triggers (las tablas puente JPA no admiten columnas nuevas NOT NULL). Un solo pago APPROVED por pedido. Claves de tenant inmutables |
| `V202610130930__append_only_tables` | sí | `forbid_update_delete()`. `audit_logs`, `login_attempts`, `payment_events`, `shipment_events` e `inventory_movements` pasan a ser de solo inserción |
| *(0935)* | — | **No existe a propósito.** El historial de precios y estados sigue el diseño predictivo (`140950`, `141020`) y no usa una tabla genérica |
| `V202610130940__fk_on_delete_and_search` | sí | `ON DELETE` explícito: CASCADE en tablas puente, SET NULL en actores y RESTRICT en finanzas, con `NOT VALID`. Columnas generadas `products.search_vector` y `part_number_normalized` (**reescribe `products`**) |
| `V202610130941__validate_foreign_keys` | sí | Diagnóstico de huérfanos y `VALIDATE` |
| `V202610130942__search_and_brin_indexes` | **no** | GIN de texto completo, trigram (nombre y referencia), `jsonb_path_ops` y BRIN de eventos |

### Fase predictiva 1 (diseño §5)

| Versión | Tx | Qué resuelve |
|---|---|---|
| `V202610140910__schemas_and_conventions` | sí | Esquemas `tracking`, `analytics`, `ml` y `privacy`. `ensure_monthly_partitions()`. `assume_utc()` y `bogota_date()` |
| `V202610140920__geo_reference` | sí | `geo_departments`, `geo_municipalities` y `municipality_code` en `locations`/`order_addresses`. Vista `analytics.dim_geography` |
| `V202610140930__calendar_dimension` | sí | `analytics.holidays` y `analytics.dim_date` (2020-2035). Funciones de Pascua y festivos de Colombia |
| `V202610140940__inventory_ledger_reasons` | sí | Catálogo de motivos. Columnas del ledger (`reason_code`, `source`, referencia, costo, idempotencia, `occurred_at`). `inventory.reserved_stock/unit_cost/...` |
| `V202610140941__inventory_ledger_opening_balance` | sí | Datos: movimiento `INITIAL_LOAD` por inventario con stock, para que el ledger cuadre |
| `V202610140950__inventory_price_history` | sí | `inventory_price_history` con validez temporal y margen calculado |
| `V202610140951__inventory_price_history_backfill` | sí | Datos: fila `INITIAL_LOAD` abierta por inventario |
| `V202610141000__inventory_reorder_policies` | sí | Puntos de reorden con historial |
| `V202610141010__inventory_daily_snapshots` | sí | Snapshot diario particionado (con `stockout_minutes` y `available`) |
| `V202610141020__order_status_history` | sí | `cancellation_reasons`, `order_status_history` y `sale_status_history`. Columnas de atribución, SLA e instantánea de línea (precio de lista, descuento, promoción, costo) en `orders`, `sales`, `order_details`, `shipments`, `shipment_events` y `payment_events`. `data_origin` en `store`/`orders` |
| `V202610141021__status_history_backfill` | sí | Datos: estado inicial de pedidos y ventas, y `order_details.product_id` |
| `V202610141025__capture_bridge_triggers` | sí | **Puente** hasta los cambios de backend: captura stock → ledger, precio → historial, estado de venta/pedido → historial, y completa `shipment_events.from_status_id`. Ver §5 |
| `V202610141030__privacy_consents_pseudonyms` | sí | `consent_purposes`, `person_consents` (solo inserción), vista del estado actual y `privacy.subject_pseudonyms` |
| `V202610141040__tracking_events` | sí | Catálogo de eventos y `tracking.sessions/search_events/product_events/events`, particionadas. `identity_links` |
| `V202610141050__order_attribution` | sí | Atribución de canal por pedido |
| `V202610141100__analytics_facts_phase1` | sí | `fact_sales_daily`, `fact_order_fulfillment` y `fact_unmet_demand_daily`, con sus funciones de refresco |

### Fase predictiva 2 (diseño §6)

| Versión | Qué resuelve |
|---|---|
| `V202610141200__customer_garage` | Atributos técnicos de `versions`, `customer_vehicles`, `vehicle_odometer_readings` y FK `order_details.customer_vehicle_id` |
| `V202610141201__validate_customer_vehicle_fk` | `VALIDATE` de la FK `order_details.customer_vehicle_id`, creada `NOT VALID` en 141200 |
| `V202610141210__compatibility_evidence` | Evidencia en `products_compatibilies`. Ver la advertencia H5 en §6 |
| `V202610141220__carts` | `carts`, `cart_items` |
| `V202610141230__stock_reservations` | `stock_reservations` |
| `V202610141240__purchasing` | Proveedores, órdenes de compra y recepciones |
| `V202610141250__returns` | Motivos, solicitudes, líneas e historial de devoluciones y garantías |
| `V202610141300__ml_registry_and_features` | Modelos, datasets, versiones, ejecuciones, features (particionadas), predicciones (particionadas) y resultados |
| `V202610141310__payment_risk` | Columnas de `payments`, `seller_payments.environment` (nullable) y `payment_risk_assessments` |
| `V202610141320__analytics_phase2` | Demanda por vehículo, embudo de búsqueda, devoluciones, vistas materializadas (rotación, cohortes, RFM) y lead time de proveedores |
| `V202610141330__phase_tenant_consistency` | Coherencia de `store_id`/`branch_id` desnormalizados en las tablas nuevas |

Todas las de la fase 2 son transaccionales.

### Repetibles (después de todas las V)

- `R__seed_geo_divipola.sql`: los 33 departamentos (32 + Bogotá D.C.). Los municipios se cargan con `pending-approval/load_divipola_municipalities.sql` desde el CSV oficial del DANE (A10).
- `R__seed_holidays_co.sql`: festivos nacionales 2020-2035 y fechas comerciales. Vuelve a poblar `dim_date`.

## 3. Cómo aplicar

Antes de empezar:

```bash
pg_dump -Fc "$DATABASE_URL" -f autopartco_pre_migrations.dump
```

### 3.1 Con psql

- Los archivos **sin** `.sql.conf` se ejecutan en una sola transacción: `--single-transaction`.
- Los archivos **con** `.sql.conf` (0910, 0915, 0942) usan `CREATE/DROP INDEX CONCURRENTLY`. Esa sentencia **no puede ir dentro de una transacción**, así que se ejecutan sin `--single-transaction`.

En Windows, fije la codificación del cliente: sin ella `psql` envía los archivos (UTF-8) como WIN1252 y falla con "carácter 0x8d ... no tiene equivalente en UTF8".

```bash
export PGCLIENTENCODING=UTF8
cd database/migrations
for f in $(ls V*.sql | sort); do
  echo ">> $f"
  if [ -f "$f.conf" ]; then
    psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$f" || break
  else
    psql "$DATABASE_URL" -v ON_ERROR_STOP=1 --single-transaction -f "$f" || break
  fi
done
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 --single-transaction -f R__seed_geo_divipola.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 --single-transaction -f R__seed_holidays_co.sql
psql "$DATABASE_URL" -c "ANALYZE;"
```

Para un rollback, aplique los `U` en **orden inverso**. Si el `U` es destructivo:

```bash
PGOPTIONS="-c autopartco.allow_destructive=on" psql "$DATABASE_URL" -v ON_ERROR_STOP=1 --single-transaction -f U2026...sql
```

### 3.2 Con Flyway (cuando se apruebe A1)

- `spring.flyway.locations`: este directorio. El ADR propone empaquetarlo como `classpath:db/migration`.
- **Archivos sin transacción:** Flyway lee `V…__x.sql.conf` (`executeInTransaction=false`). La primera línea `-- flyway:executeInTransaction=false` de esos scripts es documentación; la configuración efectiva es el `.conf`. Sin él, Flyway falla en `CREATE INDEX CONCURRENTLY`.
- **Línea base.** El esquema de `init.sql` ya existe. Hay dos opciones:
  1. BD existente: `baseline-on-migrate: true`, `baseline-version: 202610130000`.
  2. BD nueva: convertir `init.sql` en `V202610130000__baseline_schema.sql`. **El ADR propone `V202610140900__baseline_schema.sql`, que quedaría DESPUÉS de estas migraciones (0913…) y rompería el orden. Hay que renumerarla a un valor menor que `202610130900`.**
- **Archivos `U`:** Undo es una función de pago de Flyway. El backend debe confirmar que Community ignora los `U` sin fallar en `validate` (ADR §2.2, regla 4). Si falla, se mueven a `database/rollback/`, lo que requiere aprobación.
- `pending-approval/`: el prefijo `PA_` impide que Flyway reconozca los archivos aunque recorra subcarpetas. Con `validate-migration-naming: true`, Flyway rechazaría los nombres no estándar: en ese caso, excluya la carpeta del recurso empaquetado.
- Flyway sustituye `${…}` como placeholders. Ningún archivo los usa.
- Cada migración transaccional empieza con `SET LOCAL lock_timeout/statement_timeout`. Las no transaccionales usan `SET` y `RESET` al final, para no dejar la conexión del pool con esos valores.

## 4. Requieren aprobación (`pending-approval/`)

Todos llevan la cabecera `-- REQUIERE APROBACIÓN`, el plan de respaldo y su `PA_U…` de rollback. **Al aprobar uno:**

1. Quite el prefijo `PA_`.
2. Asígnele un timestamp **nuevo**, posterior a la última migración aplicada. Con `outOfOrder=false`, Flyway rechaza versiones antiguas.
3. Muévalo a `database/migrations/`.
4. Si es no transaccional, cree su `.sql.conf`.

| Archivo | Ref. | Qué hace | Requiere en backend |
|---|---|---|---|
| `PA_V202610140945__inventory_movements_constraints` | A4 | CHECK `movement_type IN ('IN','OUT')`, balance y dirección del motivo | `StockLedger` con IN/OUT |
| `PA_V202610141110__timestamptz_existing_tables` | A3 | Todas las columnas TIMESTAMP de `public` pasan a TIMESTAMPTZ (`AT TIME ZONE public.legacy_source_tz()`; ejecutar con `autopartco.legacy_source_tz = 'America/Bogota'`). Guarda y recrea las vistas dependientes. **Reescribe las tablas: con la app detenida y respaldo previo** | Requiere en backend: la JVM con zona America/Bogota fijada en `BackendApplication.main` (ADR 0002) y `ddl-auto: validate`; no fijar `hibernate.jdbc.time_zone` |
| `PA_V202610141500__contract_phase1_not_null` | A5 | NOT NULL en `inventory_movements.reason_code` y `order_details.product_id` | INSERT con esos campos |
| `PA_V202610141510__database_roles` | A6 | Roles `autopartco_migrator/app/analyst/ml`, NOLOGIN y sin contraseñas. GRANT por esquema. Revoca UPDATE/DELETE en las tablas de solo inserción | `ddl-auto: validate` antes de usar `autopartco_app` |
| `PA_V202610141520__retention_functions` | A9 | `detach_and_drop_partitions_older_than` y purgas de IP y de identity_links (dry-run por defecto) | `RetentionJob` |
| `load_divipola_municipalities.sql` | A10 | Carga de municipios desde el CSV oficial (script manual) | — |
| `PA_V202610131000__rename_typos` | aud. 22 | `products_compatibilies` → `product_compatibilities`, `bussnies_*` → `business_*` | `Product.java`, `Store.java` y SQL nativo (`ListingQueries`) |
| `PA_V202610131010__products_sku_unique_per_store` | aud. 6 | SKU único por almacén (no transaccional) | Quitar `unique = true` de `Product.sku` |
| `PA_V202610131020__states_per_domain` | aud. 15 | `state_domains` y un trigger que valida el estado por dominio | Ninguno, si el mapeo es correcto |
| `PA_V202610131025__soft_delete_partial_uniques` | aud. 6/21 | UNIQUE parciales en colaboradores, roles y sucursales. `UNIQUE(carrier, tracking_number)` (no transaccional) | Ninguno |
| `PA_V202610131030__inventory_price_positive` | aud. 18 | `price > 0` | `@DecimalMin(inclusive = false)` antes |

## 5. Variables de sesión que usan las migraciones

| Variable | Uso |
|---|---|
| `autopartco.allow_destructive = on` | Habilita los `U` destructivos y los `PA_` con guard |
| `autopartco.allow_purge = on` | Permite DELETE/TRUNCATE en tablas de solo inserción (job de retención, A9). **No es una barrera de seguridad:** la barrera real es A6 |
| `autopartco.capture_managed_by_app = on` | Desactiva los triggers puente de `V202610141025` en esa transacción. Úselo cuando `StockLedger`, `ListingPrices` y `SaleStatusService` escriban directamente |
| `autopartco.actor_account_id = '<uuid>'` | Actor para los historiales capturados por trigger (opcional) |
| `autopartco.actor_type = STORE\|CUSTOMER\|PLATFORM\|SYSTEM\|PROVIDER` | Tipo de actor (opcional) |

Desde Java: `jdbcTemplate.execute("SET LOCAL autopartco.capture_managed_by_app = 'on'")` dentro de la transacción. También vale `SELECT set_config('autopartco.actor_account_id', ?, true)`.

## 6. Impacto en el backend (lo aplica el agente `backend`)

Ninguna migración del flujo normal exige cambios para seguir funcionando. Recomendado:

1. **Consultas de email:** `findByEmailIgnoreCase` genera `upper(email)` (lo sirve `idx_accounts__email_upper`). Unificar con `lower()` permitiría eliminar ese índice.
2. **Invitaciones:** `StaffInvitationRepository.findPendingByEmail` usa `upper(email)` y lo sirve un índice nuevo. Sería mejor usar `lower()` para aprovechar el índice único existente.
3. **Teléfonos:** `RegisterRequest`/`ProfileUpdateRequest` deben añadir `@Pattern` (dígitos y `+ ( ) . -`, de 7 a 15 dígitos). Si no, un teléfono con letras da 500 por `chk_persons__number_phone_format`.
4. **Errores de BD:** los triggers lanzan SQLSTATE `23514`/`23505` (Spring los traduce a `DataIntegrityViolationException`), y las tablas de solo inserción `42501`. Conviene mapearlos a 409/400.
5. **AuditLog** tiene `@SQLDelete`. Ahora un borrado falla (solo inserción). No hay código que lo invoque.
6. **H5:** mapear `products_compatibilies` como entidad antes de escribir evidencia (`V202610141210`).
7. **Fase 1 (diseño §12):** `StockLedger`, `ListingPrices`, el historial en `SaleStatusService` y el webhook a `payment_events`. Al implementarlos, fijar `autopartco.capture_managed_by_app = 'on'` o retirar el puente (`U202610141025`).
8. **Jobs:** `PartitionMaintenanceJob` (llama a `ensure_monthly_partitions(..., 3)` y alerta si la `default` tiene filas), `InventorySnapshotJob`, `AnalyticsRefreshJob`, `CartAbandonmentJob` y `RetentionJob` (A9, dry-run).
9. **Entidades:** ninguna mapea las columnas nuevas. Con `ddl-auto: validate` no hay problema. Con `update`, Hibernate no las toca.
10. **`seller_payments.environment`:** rellenarlo según la configuración de Wompi.

## 7. Verificación

### 7.1 Después de aplicar

```sql
-- Índices inválidos (debe dar 0 filas)
SELECT c.relname FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid WHERE NOT i.indisvalid;

-- Restricciones sin validar (debe dar 0 filas tras 0921/0941)
SELECT conrelid::regclass, conname FROM pg_constraint WHERE NOT convalidated;

-- El ledger cuadra con el stock
SELECT i.id, i.stock, m.new_stock
FROM inventory i
LEFT JOIN LATERAL (SELECT new_stock FROM inventory_movements
                   WHERE inventory_id = i.id ORDER BY occurred_at DESC, created_at DESC LIMIT 1) m ON true
WHERE i.deleted_at IS NULL AND i.stock IS DISTINCT FROM coalesce(m.new_stock, 0);

-- UNIQUE duplicados creados por ddl-auto: update (uk*/UK* con las mismas columnas que otro)
SELECT conrelid::regclass, array_agg(conname) FROM pg_constraint
WHERE contype = 'u' GROUP BY conrelid, conkey HAVING count(*) > 1;

-- Particiones: la default debe estar vacía
SELECT 'tracking.search_events_default', count(*) FROM tracking.search_events_default;
```

### 7.2 EXPLAIN de las consultas clave

Ejecute `EXPLAIN (ANALYZE, BUFFERS)` con datos representativos tras `ANALYZE`.

| Consulta (origen) | SQL de prueba | Plan esperado |
|---|---|---|
| Login (`findByEmailIgnoreCase`) | `SELECT id FROM accounts WHERE upper(email) = upper('a@b.co') AND deleted_at IS NULL;` | Index Scan `idx_accounts__email_upper` |
| `AccountRepository.lockByEmail` | `SELECT id FROM accounts WHERE lower(email) = lower('a@b.co');` | Index Scan `uq_accounts__email_lower` |
| `ListingQueries.STORE_LISTINGS_SQL` | `SELECT p.id FROM products p WHERE p.store_id = '<uuid>' AND p.deleted_at IS NULL ORDER BY p.name;` | Index Scan `idx_products__store_name` (sin Sort) |
| `ListingQueries.PUBLISHED_LISTING_SQL` | `SELECT i.id FROM inventory i WHERE i.product_id = '<uuid>' AND i.deleted_at IS NULL AND i.stock > 0 ORDER BY i.price LIMIT 1;` | Index Scan `idx_inventory__product_price_in_stock` |
| `SaleQueries.list` | `SELECT s.id FROM sales s JOIN branches b ON b.id = s.branch_id WHERE b.store_id = '<uuid>' AND s.deleted_at IS NULL ORDER BY s.created_at DESC, s.id LIMIT 20;` | Nested Loop + `idx_sales__branch_created` |
| `SaleQueries.list` (LATERAL pagos) | `SELECT * FROM payments WHERE order_id = '<uuid>' ORDER BY created_at DESC LIMIT 1;` | Index Scan `idx_payments__order_created` |
| `AccountSessionRepository` | `SELECT id FROM account_sessions WHERE account_id = '<uuid>' AND revoked_at IS NULL AND deleted_at IS NULL ORDER BY created_at DESC;` | Index Scan `idx_account_sessions__account_active` |
| `StaffInvitationRepository.findPendingByEmail` | `SELECT id FROM seller_collaborator_invitations WHERE store_id = '<uuid>' AND upper(email) = upper('x@y.co') AND accepted_at IS NULL AND revoked_at IS NULL AND deleted_at IS NULL;` | Index Scan `idx_seller_collaborator_invitations__store_email_upper` |
| Búsqueda futura (texto) | `SELECT id FROM products WHERE deleted_at IS NULL AND search_vector @@ websearch_to_tsquery('spanish', immutable_unaccent('pastillas freno'));` | Bitmap Index Scan `idx_products__search_vector` |
| Búsqueda futura (referencia) | `SELECT id FROM products WHERE deleted_at IS NULL AND part_number_normalized LIKE 'ABC123%';` | Bitmap Index Scan `idx_products__part_number_normalized_trgm` |
| Compatibilidad | `SELECT product_id FROM products_compatibilies WHERE compatibility_id = '<uuid>';` | Index Only Scan `idx_products_compatibilies__compatibility_product` |
| Búsquedas sin resultado (tracking) | `SELECT query_hash, count(*) FROM tracking.search_events WHERE occurred_at >= now() - interval '1 day' AND results_count = 0 GROUP BY 1;` | Poda de particiones + `idx_search_events__zero_results` |

### 7.3 Funciones de refresco (humo)

```sql
SELECT analytics.refresh_fact_sales_daily(current_date - 35, current_date - 1);
SELECT analytics.refresh_fact_order_fulfillment(now() - interval '48 hours');
SELECT analytics.refresh_fact_unmet_demand_daily(current_date - 3, current_date - 1);
REFRESH MATERIALIZED VIEW CONCURRENTLY analytics.mv_inventory_rotation_monthly;
```

## 8. Riesgos y decisiones abiertas

- **Versión de PostgreSQL:** "16+" es un mínimo, no una versión fijada. Última prueba (2026-10-14, contenedor `postgres:16`, 16.15, BD desechable con solo datos de semilla): `init.sql` → todas las `V` → seeds `R__` (2 veces) → todas las `U` en orden inverso → todas las `V` → seeds (2 veces) sin errores, y con el mismo conjunto de índices en las dos aplicaciones. Tras las `U`, el esquema coincide con `init.sql` salvo 3 tablas TOAST (`inventory_movements`, `payment_events`, `payments`) que PostgreSQL no elimina al borrar columnas. No se probaron `VALIDATE` ni backfills con volumen real ni los `PA_`.

- **Numeración frente al ADR:** la baseline `V202610140900` del ADR debe renumerarse por debajo de `202610130900`, o hay que usar `baseline-version` (§3.2).
- **Idempotencia frente a la regla 7 del ADR:** se mantuvo `IF NOT EXISTS` porque la tarea lo pidió. El arquitecto debe decidir si la regla 7 se relaja.
- **0940 reescribe `products`** (columnas generadas): aplíquela en una ventana de baja carga.
- **A3 (timestamptz)** reescribe todas las tablas y exige el cambio de backend a la vez.
- **Triggers puente:** si el backend escribe el ledger o el historial con `occurred_at`/`changed_at` distintos de `now()` sin fijar `capture_managed_by_app`, habrá duplicados.
- **`gin_trgm_ops` sin calificar:** 0942 fija `search_path = public, extensions` para cubrir Supabase. Si `pg_trgm` está instalado en otro esquema, añádalo a esa línea.
- **PostgreSQL 16 no admite IDENTITY en tablas particionadas.** Los `id` de `tracking.*` y `ml.predictions` usan secuencias.
- **`tracking.event_definitions.pii_reviewed = false`:** hasta que gobierno de datos los revise, el backend rechazará los eventos.
- `natural_region` de los departamentos usa la región predominante.

## 9. Fase 3 (solo propuesta, sin migraciones)

Según el diseño §7, sin implementar hasta que haya un caso de uso:

- `vehicle_service_records`, `maintenance_schedules` (intervalos con fuente documentada) y `maintenance_reminders`.
- `inventory_transfers`/`_items` y `stock_counts`/`_items`, que generan `TRANSFER_*` y `COUNT_ADJUSTMENT`.
- `payment_disputes` (etiqueta `CHARGEBACK`).
- `ai_interactions` (proveedor, modelo, prompt versionado, latencia, tokens, costo y `subject_key`; sin texto libre por defecto).
- `analytics.fx_rates_daily` (TRM) y parque automotor por departamento. **Requieren aprobación** por ser datos externos.
- `pgvector` para búsqueda semántica (**aprobación:** extensión).
- Esquema `pii` o cifrado por columna para `persons`, direcciones y VIN (**aprobación:** destructivo y de seguridad).
- RLS por `store_id` en `analytics`/`ml` (**aprobación:** seguridad).
- Particionar `inventory_movements` al superar unos 50 M de filas (**aprobación:** recrear la tabla).
- De la auditoría, pendientes de diseño: `login_attempts.account_id` nullable + `email_attempted`, `persons.identification_type`, `sha256` en documentos, `audit_logs.ip_address/user_agent/request_id/actor_type`, `used_at` en tokens y una tabla de eventos de webhook de Wompi para `seller_payments`.
