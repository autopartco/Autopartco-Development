# Diseño: evolución de la base de datos para datos de alto valor predictivo

- Estado: **Propuesto** (solo diseño; lo implementa el agente `database` como migraciones Flyway según el ADR 0001)
- Fecha: 2026-10-13
- Autor: agente `arquitecto`
- Entradas revisadas: `database/init.sql` (54 tablas), entidades JPA de `backend/src/main/java/com/autopartco/backend/core/**/internal`, `core/order/SaleStatusService.java`, `core/order/ShipmentService.java`, `core/inventory/StockService.java`, `docs/AUTOPARTCO_PRODUCT_BLUEPRINT.md` §5, §6, §10-12, `docs/AUTOPARTCO_IDEA.md`, brechas §11 de `docs/architecture/2026-10-13-backend-modular-architecture-design.md`
- Depende de: `docs/adr/0001-estrategia-de-esquema-y-migraciones.md`

## Índice

1. Objetivo y principios
2. Supuestos y hallazgos que condicionan el diseño
3. Convenciones
4. Esquemas, roles y particionamiento
5. Fase 1 (imprescindible)
6. Fase 2
7. Fase 3
8. Privacidad
9. Retención
10. Plan de migraciones (archivos)
11. Cambios que requieren aprobación
12. Impacto en backend
13. Orden de implementación por agente y archivos
14. Abierto para los implementadores

---

## 1. Objetivo y principios

Capturar **desde ya** los datos que no se pueden reconstruir después: precios vigentes en cada momento, existencias diarias, búsquedas sin resultado, tiempos de cada transición y motivos tipificados. Así, cuando haya historial suficiente, Autopartco podrá entrenar y evaluar modelos de:

- demanda por producto y sucursal;
- reposición (punto de reorden y lead time);
- elasticidad y precio;
- tiempo de entrega y SLA;
- fraude;
- mantenimiento predictivo del garaje;
- confianza de compatibilidad.

Principios:

1. **Lo que no se registra hoy se pierde.** Por eso la fase 1 prioriza la captura (historiales, snapshots, eventos) frente a la explotación (feature store y modelos).
2. **Ninguna predicción sin línea base ni trazabilidad.** El blueprint §10.4 y §11.3 piden horizonte, fecha de cálculo, datos usados, línea base y error. El esquema `ml` lo hace obligatorio.
3. **Censura de demanda explícita.** Si un día hubo cero ventas porque no había stock, ese día no tuvo demanda cero. Los snapshots registran `available` y `stockout_minutes` para que los modelos distingan ambos casos.
4. **`NULL` significa "no medido" y `0` significa "medido y nulo".** Por ejemplo, `in_transit` queda en `NULL` mientras no existan órdenes de compra.
5. **Datos reales, de demostración y sintéticos separados** (`data_origin`), como exige `AUTOPARTCO_IDEA.md` para datos sintéticos.
6. **La analítica y el ML nunca ven PII directa.** Usan un seudónimo revocable (`subject_key`).
7. **Monolito modular.** PostgreSQL resuelve todo con esquemas, particionamiento declarativo y funciones SQL. No se usa Kafka, ni un data warehouse aparte, ni extensiones sin aprobación.

## 2. Supuestos y hallazgos que condicionan el diseño

- **País: Colombia.** El encargo menciona "festivos MX", pero el producto está definido para Colombia (`AUTOPARTCO_AGENT_CONTEXT.md`, `AUTOPARTCO_IDEA.md`). El calendario se diseña con `country_code` y se siembran los festivos de **CO** (Ley 51 de 1983, "Ley Emiliani"). México se añadiría solo con filas nuevas, sin cambiar el esquema. La geografía usa **DIVIPOLA (DANE)**. La privacidad se basa en la **Ley 1581 de 2012 y el Decreto 1377 de 2013** (habeas data). La moneda es COP.
- **H1. El ledger de inventario no se escribe.** `StockService.updateListing` hace `inventory.setStock(...)` sin crear `inventory_movements` (brecha DB-INV-1). Sin ledger no hay ventas fuera de línea, ni mermas, ni snapshots reconciliables. Corregirlo es el **primer** cambio de backend de la fase 1.
- **H2. No hay historial de estados de venta.** `SaleStatusService.updateSaleStatus` sobrescribe `sales.state_id`. Solo los envíos tienen historial (`shipment_events`).
- **H3. El precio se pierde.** `inventory.price` se sobrescribe y `order_details` guarda solo `unit_price`, sin precio de lista, descuento, promoción ni costo. Sin esos datos no se puede estimar la elasticidad.
- **H4. Zonas horarias mezcladas.** Ver ADR 0001 §1. Todas las tablas nuevas usan `TIMESTAMPTZ`. Los agregados diarios usan la fecha local `America/Bogota`.
- **H5. `products_compatibilies` está mapeada con `@ElementCollection`.** Hibernate borra y reinserta la colección completa en cada cambio. Si se le añaden columnas de evidencia (fase 2), esas columnas se perderían. Antes hay que mapearla como entidad (ver §12).
- **H6. Posible exposición vía Supabase.** `backend/.env.example` declara claves de Supabase. Si la Data API (PostgREST) del proyecto está habilitada, las tablas de `public` podrían quedar expuestas sin RLS. `qa-seguridad` debe comprobarlo. Los esquemas nuevos no deben añadirse a los esquemas expuestos.

## 3. Convenciones

Aplican a todo objeto nuevo. Los objetos existentes conservan su nombre: `store`, `make`, `model`, `versions`, `products_compatibilies` y `bussnies_*` no se renombran, porque hacerlo requiere aprobación.

| Tema | Regla |
|---|---|
| Nombres | `snake_case`, tablas en plural, columnas en singular. FK `<entidad>_id`. Booleanos `is_`/`has_` o participio (`restocked`). Fechas-hora `<evento>_at`; fechas `<evento>_on` o `<x>_date`. |
| Restricciones | `pk_<tabla>`, `fk_<tabla>__<columna>`, `uq_<tabla>__<columnas>`, `chk_<tabla>__<regla>`. Índices: `idx_<tabla>__<columnas>`, `brin_<tabla>__<columna>`. |
| Claves primarias | **UUID** (`gen_random_uuid()`) para entidades que expone la API (convención `docs/api/conventions.md` §12). **`BIGINT GENERATED ALWAYS AS IDENTITY`** para tablas de solo inserción y gran volumen (historiales, eventos, snapshots, predicciones): índices más pequeños, orden natural y buena correlación con BRIN. |
| Tiempo | `TIMESTAMPTZ` siempre. Fecha de negocio `DATE` calculada como `(ts AT TIME ZONE 'America/Bogota')::date`. |
| Dinero | `NUMERIC(12,2)` por unidad o línea (coherente con el `DECIMAL(12,2)` existente). `NUMERIC(16,2)` en agregados. Costos `NUMERIC(14,4)` (promedios ponderados). Siempre con `currency CHAR(3)` donde el importe salga de la fila. Nunca `float`. |
| Tasas | `NUMERIC(5,4)` (IVA 0.1900). Márgenes `NUMERIC(9,4)`. Probabilidades `NUMERIC(6,5)`. |
| Enumeraciones | Conjunto cerrado y estable: `VARCHAR(n)` + `CHECK`, como ya hace `seller_applications.status`. Motivos que el negocio amplía: **tabla de catálogo con `code VARCHAR(40)` como PK** y FK. **No** se usan tipos `ENUM` de PostgreSQL, porque son difíciles de alterar. |
| Columnas comunes (tablas mutables) | `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` con el trigger `trg_<tabla>_updated_at BEFORE UPDATE ... EXECUTE FUNCTION set_updated_at()` (la función ya existe en `init.sql` y se reutiliza). `deleted_at` y `version BIGINT` solo si hay borrado lógico o bloqueo optimista (entidad JPA). |
| Tablas de solo inserción | Solo `created_at` u `occurred_at`. Sin `updated_at`, `deleted_at` ni `version`. Trigger `trg_<tabla>_append_only` que rechaza `UPDATE` y `DELETE` (función `forbid_update_delete()`). Excepciones explícitas: columnas de resultado como `outcome`. La retención se aplica eliminando particiones, nunca con `DELETE`. |
| JSONB | Solo para atributos variables con claves en **lista blanca** validada en el backend. Se limita con `CHECK (pg_column_size(col) <= N)`. Nunca para datos que se filtran o agregan con frecuencia. |
| Índices | B-tree para búsquedas selectivas (FK, `(entidad, tiempo DESC)`). **BRIN** (`pages_per_range = 32`) en la columna de tiempo de tablas de solo inserción. Índices parciales para "registro vigente". Toda FK nueva lleva índice. |
| Tenancy | Toda tabla con datos de un almacén lleva `store_id` (y `branch_id` si aplica), aunque sea derivable, para filtrar en el servidor y, en el futuro, aplicar RLS. |

Funciones de soporte (en `V202610140910__schemas_and_conventions.sql`):

- `public.set_updated_at()`: ya existe; se recrea idéntica en la línea base.
- `public.forbid_update_delete()`: `RAISE EXCEPTION 'append-only table %', TG_TABLE_NAME`.
- `public.ensure_monthly_partitions(p_parent regclass, p_from date, p_months int) RETURNS int`. Crea las particiones `<tabla>_pYYYYMM` que falten, `FOR VALUES FROM (<mes>) TO (<mes+1>)`. Detecta el tipo de la clave de partición: si es `timestamptz`, usa límites `'YYYY-MM-01 00:00:00+00'`; si es `date`, `'YYYY-MM-01'`. Devuelve cuántas creó. Es idempotente.
- `public.detach_and_drop_partitions_older_than(p_parent regclass, p_keep_months int) RETURNS int`. **Destructiva.** Solo la invoca el job de retención tras la aprobación de la política (§9).

## 4. Esquemas, roles y particionamiento

| Esquema | Contenido | Escribe | Lee |
|---|---|---|---|
| `public` | OLTP existente + tablas operativas nuevas (historiales, snapshots, compras, devoluciones, garaje, consentimientos, referencia geográfica) | backend (dominio dueño) | backend |
| `tracking` | Eventos de comportamiento crudos, sesiones y vínculos de identidad | backend (`core.tracking`) | jobs de analítica, rol analista |
| `analytics` | Dimensiones (fecha, festivos, geografía) y hechos agregados | funciones SQL invocadas por jobs | backend (paneles), rol analista |
| `ml` | Registro de modelos, datasets, features, predicciones y resultados | jobs de ML | backend, rol ML |
| `privacy` | Tabla de seudónimos `person_id → subject_key` | solo backend (`core.identity`) | solo backend |

**Roles de BD propuestos (requiere aprobación: cambio de seguridad):**

| Rol | Permisos |
|---|---|
| `autopartco_migrator` | Dueño de los objetos; DDL. Lo usa Flyway. |
| `autopartco_app` | DML en `public`. `INSERT`/`SELECT` en `tracking`. `SELECT`/`INSERT`/`DELETE` en `privacy`. `SELECT` + `EXECUTE` de funciones de refresco en `analytics`. `SELECT`/`INSERT`/`UPDATE` en `ml`. |
| `autopartco_analyst` | `SELECT` en `analytics`, `ml` y `tracking`. **Sin acceso** a `public` ni a `privacy`. |
| `autopartco_ml` | Lo mismo que el analista + `INSERT`/`UPDATE` en `ml`. |

**Particionamiento declarativo** (`PARTITION BY RANGE`, mensual):

- Tablas particionadas: `tracking.sessions`, `tracking.search_events`, `tracking.product_events`, `tracking.events`, `public.inventory_daily_snapshots`, `ml.product_branch_features`, `ml.customer_features` y `ml.predictions`.
- La PK incluye siempre la clave de partición, por ejemplo `PRIMARY KEY (occurred_at, id)`.
- Cada tabla tiene una partición `<tabla>_default` para no perder inserciones.
- El job de mantenimiento crea con 3 meses de antelación y **alerta si la `default` tiene filas**, porque impiden crear la partición del rango que ocupan.
- Mantenimiento sin `pg_partman` ni `pg_cron` (son extensiones y requieren aprobación): `PartitionMaintenanceJob` del backend llama a `ensure_monthly_partitions`. La migración inicial crea las particiones del mes actual y de los 3 siguientes.

---

## 5. Fase 1 (imprescindible)

La especificación se da en SQL. El agente `database` la traslada a las migraciones de §10 y añade los nombres de restricción de §3. "Existente" indica una tabla de `init.sql`; los cambios en ellas son **aditivos** salvo donde se marca otra cosa.

### 5.1 Referencia geográfica (DIVIPOLA)

```sql
CREATE TABLE public.geo_departments (
    code            CHAR(2)      PRIMARY KEY,              -- DIVIPOLA departamento
    name            VARCHAR(100) NOT NULL,
    natural_region  VARCHAR(20)  NOT NULL CHECK (natural_region IN
                    ('ANDINA','CARIBE','PACIFICA','ORINOQUIA','AMAZONIA','INSULAR')),
    country_code    CHAR(2)      NOT NULL DEFAULT 'CO',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE public.geo_municipalities (
    code                  CHAR(5)      PRIMARY KEY,        -- DIVIPOLA municipio
    department_code       CHAR(2)      NOT NULL REFERENCES public.geo_departments(code),
    name                  VARCHAR(150) NOT NULL,
    is_department_capital BOOLEAN      NOT NULL DEFAULT false,
    metro_area_code       VARCHAR(10),
    latitude              NUMERIC(10,7) CHECK (latitude BETWEEN -90 AND 90),
    longitude             NUMERIC(10,7) CHECK (longitude BETWEEN -180 AND 180),
    population            INTEGER CHECK (population >= 0),
    population_year       SMALLINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_geo_municipalities__department_code ON public.geo_municipalities (department_code);

-- Existentes: normalización geográfica (aditivo; relleno posterior por coincidencia de city/department)
ALTER TABLE public.locations       ADD COLUMN municipality_code CHAR(5) REFERENCES public.geo_municipalities(code);
ALTER TABLE public.order_addresses ADD COLUMN municipality_code CHAR(5) REFERENCES public.geo_municipalities(code);
CREATE INDEX idx_locations__municipality_code ON public.locations (municipality_code);

CREATE VIEW analytics.dim_geography AS
SELECT m.code AS municipality_code, m.name AS municipality_name, d.code AS department_code,
       d.name AS department_name, d.natural_region, m.is_department_capital, m.metro_area_code,
       m.latitude, m.longitude, m.population
FROM public.geo_municipalities m JOIN public.geo_departments d ON d.code = m.department_code;
```

- Semilla `R__seed_geo_divipola.sql` con los datos abiertos del DANE. **Hay que verificar la licencia** antes de incluirlos (Datos Abiertos Colombia).
- La referencia geográfica vive en `public` porque la usa el OLTP para normalizar direcciones. En `analytics` solo hay una vista.
- Si en el futuro se expande a México (INEGI también usa 5 dígitos), habría que cambiar la clave a `(country_code, code)`. Ese cambio requerirá aprobación.

### 5.2 Calendario

```sql
CREATE TABLE analytics.holidays (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    country_code   CHAR(2)      NOT NULL DEFAULT 'CO',
    region_code    VARCHAR(10),                -- NULL = nacional; si no, departamento DIVIPOLA
    holiday_date   DATE         NOT NULL,
    name           VARCHAR(120) NOT NULL,
    holiday_type   VARCHAR(15)  NOT NULL CHECK (holiday_type IN ('NATIONAL','REGIONAL','COMMERCIAL')),
    is_moved       BOOLEAN      NOT NULL DEFAULT false,   -- trasladado a lunes (Ley 51/1983)
    is_non_working BOOLEAN      NOT NULL,                  -- COMMERCIAL suele ser false
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_holidays__country_region_date_name
        UNIQUE NULLS NOT DISTINCT (country_code, region_code, holiday_date, name)
);
CREATE INDEX idx_holidays__date ON analytics.holidays (holiday_date);

CREATE TABLE analytics.dim_date (
    date                   DATE        PRIMARY KEY,
    date_key               INTEGER     NOT NULL UNIQUE,         -- AAAAMMDD
    year                   SMALLINT    NOT NULL,
    quarter                SMALLINT    NOT NULL,
    month                  SMALLINT    NOT NULL,
    month_name_es          VARCHAR(12) NOT NULL,
    iso_year               SMALLINT    NOT NULL,
    iso_week               SMALLINT    NOT NULL,
    day_of_month           SMALLINT    NOT NULL,
    day_of_week            SMALLINT    NOT NULL,                -- ISO 1=lunes
    day_name_es            VARCHAR(12) NOT NULL,
    day_of_year            SMALLINT    NOT NULL,
    week_of_month          SMALLINT    NOT NULL,
    is_weekend             BOOLEAN     NOT NULL,
    is_holiday_co          BOOLEAN     NOT NULL DEFAULT false,  -- festivo nacional no laborable
    holiday_name           VARCHAR(120),
    is_business_day_co     BOOLEAN     NOT NULL,                -- lun-sáb no festivo (comercio de autopartes abre sábados)
    business_day_of_month  SMALLINT,
    is_long_weekend        BOOLEAN     NOT NULL DEFAULT false,  -- "puente festivo"
    days_to_next_holiday   SMALLINT,
    days_from_prev_holiday SMALLINT,
    is_payday_quincena     BOOLEAN     NOT NULL,                -- día 15 o último día del mes
    days_from_last_payday  SMALLINT    NOT NULL,
    is_prima_period        BOOLEAN     NOT NULL,                -- 15-30 jun y 1-20 dic (prima de servicios)
    commercial_event       VARCHAR(120)                         -- Black Friday, día sin IVA...
);
```

- `analytics.easter_sunday(p_year int) RETURNS date` usa el algoritmo gregoriano anónimo.
- `analytics.co_national_holidays(p_year int) RETURNS TABLE(holiday_date date, name text, is_moved boolean)` calcula los festivos:
  - Fijos: 1 ene, 1 may, 20 jul, 7 ago, 8 dic y 25 dic.
  - Trasladables al lunes siguiente: 6 ene, 19 mar, 29 jun, 15 ago, 12 oct, 1 nov y 11 nov.
  - Basados en Pascua: Jueves Santo (P−3), Viernes Santo (P−2), Ascensión (P+43), Corpus Christi (P+64) y Sagrado Corazón (P+71).
- `analytics.populate_dim_date(p_from date, p_to date)` hace un upsert sobre `dim_date`. `V...__calendar_dimension.sql` lo ejecuta para 2020-01-01 a 2035-12-31.
- `R__seed_holidays_co.sql` inserta los festivos de 2020 a 2035 desde `co_national_holidays` y los eventos `COMMERCIAL` conocidos (los días sin IVA se añaden por año cuando se decretan). Después vuelve a llamar a `populate_dim_date` para refrescar las columnas de festivos. Como es repetible, se vuelve a aplicar si cambia su contenido.

### 5.3 Ledger de inventario con motivos tipificados (corrige H1)

```sql
CREATE TABLE public.inventory_movement_reasons (
    code                VARCHAR(40)  PRIMARY KEY,
    direction           VARCHAR(3)   NOT NULL CHECK (direction IN ('IN','OUT','ANY')),
    counts_as_demand    BOOLEAN      NOT NULL,   -- venta real a un cliente (señal de demanda)
    counts_as_shrinkage BOOLEAN      NOT NULL DEFAULT false,
    requires_reference  BOOLEAN      NOT NULL DEFAULT false,
    description         VARCHAR(255) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

Semilla (en la `V`, porque el código depende de ella):

| code | direction | demand | shrinkage | ref |
|---|---|---|---|---|
| `INITIAL_LOAD` | IN | f | f | f |
| `PURCHASE_RECEIPT` | IN | f | f | t |
| `MARKETPLACE_SALE` | OUT | **t** | f | t |
| `POS_SALE` (venta en mostrador o informada por ERP) | OUT | **t** | f | f |
| `SALE_CANCELLED_RESTOCK` | IN | f | f | t |
| `CUSTOMER_RETURN` | IN | f | f | t |
| `RETURN_TO_SUPPLIER` | OUT | f | f | f |
| `TRANSFER_OUT` / `TRANSFER_IN` | OUT / IN | f | f | t |
| `COUNT_ADJUSTMENT` | ANY | f | t | f |
| `DAMAGE` / `LOSS_THEFT` / `OBSOLESCENCE` | OUT | f | t | f |
| `ERP_SYNC` | ANY | f | f | t |
| `CSV_IMPORT` | ANY | f | f | t |
| `MANUAL_CORRECTION` | ANY | f | f | f |

```sql
-- Existente: inventory_movements (hoy ningún código la escribe)
ALTER TABLE public.inventory_movements
    ADD COLUMN reason_code      VARCHAR(40)  REFERENCES public.inventory_movement_reasons(code),  -- NOT NULL en contracción
    ADD COLUMN source           VARCHAR(10)  CHECK (source IN ('USER','ORDER','ERP','CSV','JOB','SYSTEM')),
    ADD COLUMN actor_account_id UUID         REFERENCES public.accounts(id),
    ADD COLUMN reference_type   VARCHAR(30)  CHECK (reference_type IN ('ORDER_DETAIL','PURCHASE_RECEIPT_ITEM',
                                  'TRANSFER_ITEM','RETURN_ITEM','SYNC_RUN','IMPORT_JOB','STOCK_COUNT')),
    ADD COLUMN reference_id     UUID,
    ADD COLUMN unit_cost        NUMERIC(14,4) CHECK (unit_cost >= 0),
    ADD COLUMN occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ADD COLUMN idempotency_key  VARCHAR(120),
    ADD COLUMN notes            VARCHAR(255);

-- REQUIERE APROBACIÓN (restricciones nuevas sobre una tabla existente; hoy está vacía porque ningún código la escribe):
ALTER TABLE public.inventory_movements
    ADD CONSTRAINT chk_inventory_movements__type    CHECK (movement_type IN ('IN','OUT')),
    ADD CONSTRAINT chk_inventory_movements__balance CHECK (
        new_stock = previous_stock + CASE movement_type WHEN 'IN' THEN quantity ELSE -quantity END);

CREATE UNIQUE INDEX uq_inventory_movements__idempotency_key ON public.inventory_movements (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_inventory_movements__inventory_occurred ON public.inventory_movements (inventory_id, occurred_at DESC);
CREATE INDEX idx_inventory_movements__reference ON public.inventory_movements (reference_type, reference_id);
CREATE INDEX brin_inventory_movements__occurred_at ON public.inventory_movements USING brin (occurred_at)
    WITH (pages_per_range = 32);
-- Trigger trg_inventory_movements_append_only (forbid_update_delete).
-- updated_at/deleted_at/version heredados del bloque DO de init.sql se mantienen (no se borran).

-- Existente: inventory
ALTER TABLE public.inventory
    ADD COLUMN reserved_stock   INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN unit_cost        NUMERIC(14,4) CHECK (unit_cost >= 0),   -- costo vigente; dato sensible, nunca al marketplace
    ADD COLUMN last_movement_at TIMESTAMPTZ,
    ADD COLUMN last_counted_at  TIMESTAMPTZ,
    ADD CONSTRAINT chk_inventory__reserved CHECK (reserved_stock >= 0 AND reserved_stock <= stock);
```

- `inventory_movements` no se particiona: convertir una tabla existente en particionada exige recrearla. BRIN basta hasta unos 50 M de filas. Se revisa en la fase 3.
- **Reconciliación:** `inventory.stock` debe ser igual al `new_stock` del último movimiento. El job nocturno de snapshots registra las discrepancias en `audit_logs` (`action = 'INVENTORY_RECONCILIATION_MISMATCH'`).

### 5.4 Puntos de reorden con historial

```sql
CREATE TABLE public.inventory_reorder_policies (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id          UUID        NOT NULL REFERENCES public.inventory(id),
    store_id              UUID        NOT NULL REFERENCES public.store(id),
    reorder_point         INTEGER     NOT NULL CHECK (reorder_point >= 0),
    safety_stock          INTEGER     NOT NULL DEFAULT 0 CHECK (safety_stock >= 0),
    reorder_quantity      INTEGER     CHECK (reorder_quantity > 0),
    max_stock             INTEGER     CHECK (max_stock IS NULL OR max_stock >= reorder_point),
    review_period_days    SMALLINT    CHECK (review_period_days > 0),
    source                VARCHAR(20) NOT NULL CHECK (source IN ('MANUAL','RULE','MODEL_SUGGESTION','ERP')),
    prediction_id         BIGINT,     -- ml.predictions.id (sin FK: tabla particionada; se valida en servicio)
    model_version_id      UUID,       -- FK a ml.model_versions se añade en fase 2
    valid_from            TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to              TIMESTAMPTZ,
    created_by_account_id UUID        REFERENCES public.accounts(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_inventory_reorder_policies__validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE UNIQUE INDEX uq_inventory_reorder_policies__current ON public.inventory_reorder_policies (inventory_id)
    WHERE valid_to IS NULL;
CREATE INDEX idx_inventory_reorder_policies__store ON public.inventory_reorder_policies (store_id);
```

Guardar el historial permite evaluar después si una política (manual o sugerida por modelo) redujo los quiebres de stock.

### 5.5 Historial de precios y costos por sucursal (corrige H3)

```sql
CREATE TABLE public.inventory_price_history (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inventory_id          UUID          NOT NULL REFERENCES public.inventory(id),
    store_id              UUID          NOT NULL REFERENCES public.store(id),
    branch_id             UUID          NOT NULL REFERENCES public.branches(id),
    product_id            UUID          NOT NULL REFERENCES public.products(id),
    list_price            NUMERIC(12,2) NOT NULL CHECK (list_price >= 0),
    unit_cost             NUMERIC(14,4) CHECK (unit_cost >= 0),
    currency              CHAR(3)       NOT NULL DEFAULT 'COP',
    vat_rate              NUMERIC(5,4)  CHECK (vat_rate BETWEEN 0 AND 1),
    price_includes_vat    BOOLEAN       NOT NULL DEFAULT true,
    gross_margin_rate     NUMERIC(9,4)  GENERATED ALWAYS AS (
        CASE WHEN list_price > 0 AND unit_cost IS NOT NULL THEN round(
            (list_price / (1 + CASE WHEN price_includes_vat THEN coalesce(vat_rate, 0) ELSE 0 END) - unit_cost)
            / (list_price / (1 + CASE WHEN price_includes_vat THEN coalesce(vat_rate, 0) ELSE 0 END)), 4)
        END) STORED,                                    -- margen sobre precio neto de IVA
    valid_from            TIMESTAMPTZ   NOT NULL,
    valid_to              TIMESTAMPTZ,
    change_source         VARCHAR(20)   NOT NULL CHECK (change_source IN ('INITIAL_LOAD','MANUAL','CSV_IMPORT',
                              'ERP_SYNC','PRICE_RULE','MODEL_SUGGESTION','PURCHASE_RECEIPT')),
    changed_by_account_id UUID          REFERENCES public.accounts(id),
    change_reason         VARCHAR(255),
    prediction_id         BIGINT,       -- si la propuesta vino de un modelo (trazabilidad)
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_inventory_price_history__validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE UNIQUE INDEX uq_inventory_price_history__open ON public.inventory_price_history (inventory_id) WHERE valid_to IS NULL;
CREATE INDEX idx_inventory_price_history__inventory_from ON public.inventory_price_history (inventory_id, valid_from DESC);
CREATE INDEX idx_inventory_price_history__product_from   ON public.inventory_price_history (product_id, valid_from);
CREATE INDEX idx_inventory_price_history__store_from     ON public.inventory_price_history (store_id, valid_from);
```

- Para cambiar el precio o el costo, el backend cierra la fila abierta (`valid_to = now()`) e inserta la nueva **en la misma transacción** en la que actualiza `inventory.price`/`unit_cost`. El índice parcial impide que haya dos filas abiertas.
- La exclusión de solapamientos con `EXCLUDE USING gist` necesitaría la extensión `btree_gist`, que requiere aprobación. No se usa.
- Relleno en la migración: una fila `INITIAL_LOAD` por cada `inventory` con `deleted_at IS NULL`, con `valid_from = created_at` y `list_price = price`.
- El precio efectivo con promoción no se guarda aquí, porque es derivable de `promotions`. Se captura en `inventory_daily_snapshots.effective_price` y, en cada venta, en `order_details` (§5.7).
- No se particiona (bajo volumen) y la retención es indefinida.

### 5.6 Snapshots diarios de inventario

```sql
CREATE TABLE public.inventory_daily_snapshots (
    snapshot_date    DATE          NOT NULL,           -- día local America/Bogota cerrado
    inventory_id     UUID          NOT NULL,
    store_id         UUID          NOT NULL,
    branch_id        UUID          NOT NULL,
    product_id       UUID          NOT NULL,
    on_hand          INTEGER       NOT NULL,
    reserved         INTEGER       NOT NULL,
    available        INTEGER       GENERATED ALWAYS AS (on_hand - reserved) STORED,
    in_transit       INTEGER,                          -- NULL hasta que existan órdenes de compra (fase 2)
    min_on_hand      INTEGER,                          -- mínimo del día según el ledger
    stockout_minutes INTEGER       CHECK (stockout_minutes BETWEEN 0 AND 1440),  -- minutos con on_hand <= 0
    list_price       NUMERIC(12,2) NOT NULL,
    effective_price  NUMERIC(12,2),                    -- con la promoción vigente al corte
    unit_cost        NUMERIC(14,4),
    is_published     BOOLEAN       NOT NULL,
    reorder_point    INTEGER,
    units_in         INTEGER       NOT NULL DEFAULT 0,
    units_out        INTEGER       NOT NULL DEFAULT 0,
    units_sold       INTEGER       NOT NULL DEFAULT 0, -- movimientos con counts_as_demand
    captured_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (snapshot_date, inventory_id)
) PARTITION BY RANGE (snapshot_date);
CREATE INDEX idx_inventory_daily_snapshots__branch_product ON public.inventory_daily_snapshots (branch_id, product_id, snapshot_date);
CREATE INDEX idx_inventory_daily_snapshots__store_date     ON public.inventory_daily_snapshots (store_id, snapshot_date);
```

- **Sin FK**, por volumen y para permitir futuras purgas. La integridad la garantiza el job, que lee de `inventory`.
- **Cuándo se genera una fila:** si el producto está publicado, si `on_hand > 0` o si hubo movimiento en el día. Un producto publicado con stock 0 genera fila, porque es un quiebre y aporta información.
- **Job:** `InventorySnapshotJob` a las 00:15 `America/Bogota` para el día anterior. Usa `INSERT ... ON CONFLICT (snapshot_date, inventory_id) DO UPDATE`, así que es idempotente y se puede reejecutar.
- Estas filas son la **serie densa** del modelo de demanda: incluyen los días sin ventas, cosa que `analytics.fact_sales_daily` no hace.

### 5.7 Historial de estados de pedido, venta, envío y pago; cancelaciones tipificadas (corrige H2)

```sql
CREATE TABLE public.cancellation_reasons (
    code        VARCHAR(40)  PRIMARY KEY,
    actor_scope VARCHAR(10)  NOT NULL CHECK (actor_scope IN ('CUSTOMER','STORE','PLATFORM','SYSTEM')),
    description VARCHAR(255) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

- **CUSTOMER:** `CUSTOMER_CHANGED_MIND`, `CUSTOMER_FOUND_CHEAPER`, `CUSTOMER_WRONG_PART`, `CUSTOMER_DELIVERY_TOO_SLOW`.
- **STORE:** `STORE_OUT_OF_STOCK`, `STORE_PRICE_ERROR`, `STORE_CANNOT_SHIP_TO_ZONE`.
- **SYSTEM:** `STORE_NO_RESPONSE_TIMEOUT`, `PAYMENT_FAILED`, `PAYMENT_TIMEOUT`.
- **PLATFORM:** `FRAUD_SUSPECTED`, `PLATFORM_POLICY`, `DUPLICATE_ORDER`, `OTHER` (exige `notes`).

```sql
CREATE TABLE public.order_status_history (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id         UUID        NOT NULL REFERENCES public.orders(id),
    from_status      VARCHAR(50),
    to_status        VARCHAR(50) NOT NULL,                 -- states.code
    changed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type       VARCHAR(10) NOT NULL CHECK (actor_type IN ('CUSTOMER','STORE','PLATFORM','SYSTEM','PROVIDER')),
    actor_account_id UUID        REFERENCES public.accounts(id),
    reason_code      VARCHAR(40) REFERENCES public.cancellation_reasons(code),
    notes            VARCHAR(255),
    CONSTRAINT chk_order_status_history__transition CHECK (from_status IS DISTINCT FROM to_status)
);
CREATE INDEX idx_order_status_history__order_changed ON public.order_status_history (order_id, changed_at);
CREATE INDEX brin_order_status_history__changed_at ON public.order_status_history USING brin (changed_at);

CREATE TABLE public.sale_status_history (  -- misma forma que order_status_history
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id          UUID        NOT NULL REFERENCES public.sales(id),
    store_id         UUID        NOT NULL REFERENCES public.store(id),
    from_status      VARCHAR(50),
    to_status        VARCHAR(50) NOT NULL,
    changed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type       VARCHAR(10) NOT NULL CHECK (actor_type IN ('CUSTOMER','STORE','PLATFORM','SYSTEM','PROVIDER')),
    actor_account_id UUID        REFERENCES public.accounts(id),
    reason_code      VARCHAR(40) REFERENCES public.cancellation_reasons(code),
    notes            VARCHAR(255),
    CONSTRAINT chk_sale_status_history__transition CHECK (from_status IS DISTINCT FROM to_status)
);
CREATE INDEX idx_sale_status_history__sale_changed ON public.sale_status_history (sale_id, changed_at);
CREATE INDEX brin_sale_status_history__changed_at ON public.sale_status_history USING brin (changed_at);
-- Ambas: trigger append-only.

-- Existente: orders (atribución, totales, origen del dato)
ALTER TABLE public.orders
    ADD COLUMN channel                  VARCHAR(20)   NOT NULL DEFAULT 'WEB'
               CHECK (channel IN ('WEB','MOBILE_WEB','APP','STORE_ASSISTED','API')),
    ADD COLUMN session_id               UUID,          -- tracking.sessions.session_id (sin FK)
    ADD COLUMN data_origin              VARCHAR(10)   NOT NULL DEFAULT 'REAL'
               CHECK (data_origin IN ('REAL','DEMO','SYNTHETIC')),
    ADD COLUMN currency                 CHAR(3)       NOT NULL DEFAULT 'COP',
    ADD COLUMN subtotal                 NUMERIC(12,2) CHECK (subtotal >= 0),
    ADD COLUMN discount_total           NUMERIC(12,2) CHECK (discount_total >= 0),
    ADD COLUMN shipping_total           NUMERIC(12,2) CHECK (shipping_total >= 0),
    ADD COLUMN total                    NUMERIC(12,2) CHECK (total >= 0),
    ADD COLUMN placed_at                TIMESTAMPTZ,
    ADD COLUMN cancelled_at             TIMESTAMPTZ,
    ADD COLUMN cancellation_reason_code VARCHAR(40)   REFERENCES public.cancellation_reasons(code);
CREATE INDEX idx_orders__placed_at ON public.orders (placed_at);

-- Existente: store (para excluir tiendas demo del entrenamiento)
ALTER TABLE public.store
    ADD COLUMN data_origin VARCHAR(10) NOT NULL DEFAULT 'REAL' CHECK (data_origin IN ('REAL','DEMO','SYNTHETIC'));

-- Existente: sales (marcas de tiempo desnormalizadas + SLA)
ALTER TABLE public.sales
    ADD COLUMN fulfillment_type         VARCHAR(20)   CHECK (fulfillment_type IN ('SHIPPING','STORE_PICKUP')),
    ADD COLUMN confirmed_at             TIMESTAMPTZ,
    ADD COLUMN ready_at                 TIMESTAMPTZ,  -- PREPARING terminado / listo para despacho o recogida
    ADD COLUMN cancelled_at             TIMESTAMPTZ,
    ADD COLUMN cancellation_reason_code VARCHAR(40)   REFERENCES public.cancellation_reasons(code),
    ADD COLUMN promised_ship_by         TIMESTAMPTZ,
    ADD COLUMN promised_delivery_by     TIMESTAMPTZ,
    ADD COLUMN discount_total           NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_total >= 0);
CREATE INDEX idx_sales__updated_at ON public.sales (updated_at);  -- ventana de refresco incremental

-- Existente: order_details (instantánea de la línea; base de la elasticidad)
ALTER TABLE public.order_details
    ADD COLUMN product_id                 UUID          REFERENCES public.products(id),   -- NOT NULL en contracción
    ADD COLUMN product_name_snapshot      VARCHAR(200),
    ADD COLUMN sku_snapshot               VARCHAR(100),
    ADD COLUMN category_id_snapshot       UUID          REFERENCES public.categories(id),
    ADD COLUMN list_unit_price            NUMERIC(12,2) CHECK (list_unit_price >= 0),
    ADD COLUMN discount_amount            NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),  -- total de la línea
    ADD COLUMN promotion_id               UUID          REFERENCES public.promotions(id),
    ADD COLUMN unit_cost_snapshot         NUMERIC(14,4) CHECK (unit_cost_snapshot >= 0),
    ADD COLUMN vat_rate                   NUMERIC(5,4)  CHECK (vat_rate BETWEEN 0 AND 1),
    ADD COLUMN compatibility_status_shown VARCHAR(20)   CHECK (compatibility_status_shown IN
                                          ('CONFIRMED','SELLER_DECLARED','SUGGESTED','UNKNOWN')),
    ADD COLUMN customer_vehicle_id        UUID;         -- FK a customer_vehicles en fase 2
CREATE INDEX idx_order_details__product ON public.order_details (product_id);
-- Relleno: product_id desde inventory.product_id vía inventory_id.

-- Existente: shipments (promesa y geografía para predecir la entrega)
ALTER TABLE public.shipments
    ADD COLUMN estimated_delivery_at         TIMESTAMPTZ,   -- promesa mostrada al cliente al crear el envío
    ADD COLUMN origin_municipality_code      CHAR(5)  REFERENCES public.geo_municipalities(code),
    ADD COLUMN destination_municipality_code CHAR(5)  REFERENCES public.geo_municipalities(code),
    ADD COLUMN distance_km                   NUMERIC(8,2) CHECK (distance_km >= 0),
    ADD COLUMN delivery_attempts             SMALLINT NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0),
    ADD COLUMN package_weight_grams          INTEGER  CHECK (package_weight_grams > 0);

-- Existente: shipment_events
ALTER TABLE public.shipment_events
    ADD COLUMN from_status_id    UUID        REFERENCES public.shipment_statuses(id),
    ADD COLUMN source            VARCHAR(20) CHECK (source IN ('STORE','CARRIER_WEBHOOK','SYSTEM')),
    ADD COLUMN actor_account_id  UUID        REFERENCES public.accounts(id),
    ADD COLUMN municipality_code CHAR(5)     REFERENCES public.geo_municipalities(code);
-- Semilla aditiva de shipment_statuses: READY_FOR_PICKUP, FAILED_ATTEMPT, RETURNED_TO_SENDER.

-- Existente: payment_events
ALTER TABLE public.payment_events
    ADD COLUMN from_status_id    UUID         REFERENCES public.payment_statuses(id),
    ADD COLUMN source            VARCHAR(20)  CHECK (source IN ('CHECKOUT','WEBHOOK','RECONCILIATION','ADMIN')),
    ADD COLUMN provider_event_id VARCHAR(150),
    ADD COLUMN provider_status   VARCHAR(50),
    ADD COLUMN occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT now();
CREATE UNIQUE INDEX uq_payment_events__payment_provider_event ON public.payment_events (payment_id, provider_event_id)
    WHERE provider_event_id IS NOT NULL;
CREATE INDEX idx_payment_events__payment_occurred ON public.payment_events (payment_id, occurred_at);
```

- **Desnormalización controlada:** `sales.confirmed_at`, `ready_at`, `cancelled_at` y `orders.cancelled_at` se escriben en la misma transacción que la fila de historial. La fuente de verdad es el historial.
- **Relleno:** una fila `from_status = NULL, to_status = <estado actual>, changed_at = created_at, actor_type = 'SYSTEM'` por cada venta y pedido existente.

### 5.8 Atribución de canal

```sql
CREATE TABLE public.order_attributions (
    order_id                UUID         PRIMARY KEY REFERENCES public.orders(id),
    session_id              UUID,
    anonymous_id            UUID,
    first_touch_at          TIMESTAMPTZ,
    first_utm_source        VARCHAR(100),
    first_utm_medium        VARCHAR(100),
    first_utm_campaign      VARCHAR(150),
    first_utm_term          VARCHAR(150),
    first_utm_content       VARCHAR(150),
    first_referrer_host     VARCHAR(255),
    first_landing_path      VARCHAR(500),
    last_touch_at           TIMESTAMPTZ,
    last_utm_source         VARCHAR(100),
    last_utm_medium         VARCHAR(100),
    last_utm_campaign       VARCHAR(150),
    last_utm_term           VARCHAR(150),
    last_utm_content        VARCHAR(150),
    last_referrer_host      VARCHAR(255),
    device_type             VARCHAR(10)  CHECK (device_type IN ('DESKTOP','MOBILE','TABLET','OTHER','UNKNOWN')),
    os_family               VARCHAR(30),
    browser_family          VARCHAR(30),
    touches_count           SMALLINT     CHECK (touches_count >= 0),
    attribution_window_days SMALLINT     NOT NULL DEFAULT 30,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

- Solo se rellenan UTM y sesión si hay consentimiento `ANALYTICS_TRACKING` (§8). Si no lo hay, la fila guarda únicamente `device_type`, que se obtiene del User-Agent de la petición del checkout y forma parte del dato operativo.
- `landing_path` se guarda **sin query string**, salvo los parámetros `utm_*`.

### 5.9 Eventos de comportamiento (esquema `tracking`)

```sql
CREATE TABLE tracking.event_definitions (       -- catálogo y gobierno de eventos
    event_name      VARCHAR(60)  PRIMARY KEY CHECK (event_name ~ '^[a-z]+(_[a-z]+)*$'),
    target_table    VARCHAR(30)  NOT NULL CHECK (target_table IN ('search_events','product_events','events')),
    description     VARCHAR(255) NOT NULL,
    allowed_properties JSONB     NOT NULL DEFAULT '[]'::jsonb,   -- lista blanca de claves de properties
    pii_reviewed    BOOLEAN      NOT NULL DEFAULT false,         -- no se acepta si es false
    owner_module    VARCHAR(60)  NOT NULL,
    introduced_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    retired_at      TIMESTAMPTZ
);

CREATE TABLE tracking.sessions (
    session_id            UUID         NOT NULL,
    started_at            TIMESTAMPTZ  NOT NULL,
    last_seen_at          TIMESTAMPTZ  NOT NULL,
    anonymous_id          UUID         NOT NULL,             -- id aleatorio del navegador
    subject_key           UUID,                              -- seudónimo si hay sesión iniciada
    surface               VARCHAR(20)  NOT NULL CHECK (surface IN ('MARKETPLACE','STORESUITE','OPERATIONS')),
    device_type           VARCHAR(10)  NOT NULL CHECK (device_type IN ('DESKTOP','MOBILE','TABLET','OTHER','UNKNOWN')),
    os_family             VARCHAR(30),
    browser_family        VARCHAR(30),
    app_version           VARCHAR(20),
    utm_source            VARCHAR(100),
    utm_medium            VARCHAR(100),
    utm_campaign          VARCHAR(150),
    utm_term              VARCHAR(150),
    utm_content           VARCHAR(150),
    referrer_host         VARCHAR(255),
    landing_path          VARCHAR(500),
    geo_department_code   CHAR(2),                           -- derivado de la IP en servidor; la IP NO se guarda
    geo_municipality_code CHAR(5),
    is_bot                BOOLEAN      NOT NULL DEFAULT false,
    PRIMARY KEY (started_at, session_id)
) PARTITION BY RANGE (started_at);
CREATE INDEX idx_sessions__session_id   ON tracking.sessions (session_id);
CREATE INDEX idx_sessions__subject_key  ON tracking.sessions (subject_key) WHERE subject_key IS NOT NULL;
CREATE INDEX brin_sessions__started_at  ON tracking.sessions USING brin (started_at);

CREATE TABLE tracking.search_events (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id                 UUID          NOT NULL,          -- generado por el cliente; idempotencia
    occurred_at              TIMESTAMPTZ   NOT NULL,
    received_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    session_id               UUID          NOT NULL,
    anonymous_id             UUID          NOT NULL,
    subject_key              UUID,
    surface                  VARCHAR(20)   NOT NULL CHECK (surface IN ('MARKETPLACE','STORESUITE','OPERATIONS')),
    search_type              VARCHAR(20)   NOT NULL CHECK (search_type IN
                             ('TEXT','PART_NUMBER','VEHICLE','VIN','SEMANTIC','CATEGORY_BROWSE')),
    query_text               VARCHAR(200),                    -- normalizado y depurado de PII en servidor
    query_hash               CHAR(64),                        -- sha256 hex del texto normalizado
    make_id                  UUID,
    model_id                 UUID,
    version_id               UUID,
    vehicle_year             SMALLINT      CHECK (vehicle_year BETWEEN 1950 AND 2100),
    vin_wmi                  CHAR(3),                         -- solo fabricante mundial; nunca el VIN completo
    vin_model_year_code      CHAR(1),                         -- posición 10 del VIN
    vin_decoded              BOOLEAN,
    customer_vehicle_used    BOOLEAN       NOT NULL DEFAULT false,   -- se buscó desde el garaje
    category_id              UUID,
    manufacturer_id          UUID,
    store_id                 UUID,
    price_min                NUMERIC(12,2),
    price_max                NUMERIC(12,2),
    geo_municipality_code    CHAR(5),
    filters                  JSONB         NOT NULL DEFAULT '{}'::jsonb
                             CHECK (pg_column_size(filters) <= 2048),
    sort                     VARCHAR(30),
    page                     SMALLINT      NOT NULL DEFAULT 1 CHECK (page >= 1),
    results_count            INTEGER       NOT NULL CHECK (results_count >= 0),
    zero_results             BOOLEAN       GENERATED ALWAYS AS (results_count = 0) STORED,
    compatible_results_count INTEGER       CHECK (compatible_results_count >= 0),
    latency_ms               INTEGER       CHECK (latency_ms >= 0),
    PRIMARY KEY (occurred_at, id),
    CONSTRAINT uq_search_events__event UNIQUE (event_id, occurred_at)
) PARTITION BY RANGE (occurred_at);
CREATE INDEX brin_search_events__occurred_at ON tracking.search_events USING brin (occurred_at) WITH (pages_per_range = 32);
CREATE INDEX idx_search_events__session      ON tracking.search_events (session_id);
CREATE INDEX idx_search_events__zero_results ON tracking.search_events (occurred_at, query_hash) WHERE results_count = 0;
CREATE INDEX idx_search_events__vehicle      ON tracking.search_events (model_id, version_id, vehicle_year) WHERE model_id IS NOT NULL;

CREATE TABLE tracking.product_events (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id                   UUID          NOT NULL,
    occurred_at                TIMESTAMPTZ   NOT NULL,
    received_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    session_id                 UUID          NOT NULL,
    anonymous_id               UUID          NOT NULL,
    subject_key                UUID,
    event_type                 VARCHAR(30)   NOT NULL CHECK (event_type IN ('PRODUCT_VIEW','SEARCH_RESULT_CLICK',
                               'ADD_TO_CART','REMOVE_FROM_CART','CART_QUANTITY_CHANGED','CONTACT_STORE',
                               'COMPATIBILITY_CHECK','FAVORITE_ADDED','FAVORITE_REMOVED','SHARE')),
    product_id                 UUID          NOT NULL,
    inventory_id               UUID,
    store_id                   UUID,
    branch_id                  UUID,
    quantity                   INTEGER,                       -- delta en eventos de carrito
    displayed_price            NUMERIC(12,2) CHECK (displayed_price >= 0),
    displayed_list_price       NUMERIC(12,2) CHECK (displayed_list_price >= 0),
    promotion_id               UUID,
    availability_shown         VARCHAR(20)   CHECK (availability_shown IN ('IN_STOCK','LOW_STOCK','OUT_OF_STOCK','UNKNOWN')),
    compatibility_status_shown VARCHAR(20)   CHECK (compatibility_status_shown IN
                               ('CONFIRMED','SELLER_DECLARED','SUGGESTED','UNKNOWN')),
    search_event_id            UUID,                          -- event_id de la búsqueda de origen
    result_position            SMALLINT      CHECK (result_position >= 1),
    cart_id                    UUID,                          -- fase 2
    PRIMARY KEY (occurred_at, id),
    CONSTRAINT uq_product_events__event UNIQUE (event_id, occurred_at)
) PARTITION BY RANGE (occurred_at);
CREATE INDEX brin_product_events__occurred_at  ON tracking.product_events USING brin (occurred_at) WITH (pages_per_range = 32);
CREATE INDEX idx_product_events__product_type  ON tracking.product_events (product_id, event_type, occurred_at);
CREATE INDEX idx_product_events__session       ON tracking.product_events (session_id);

CREATE TABLE tracking.events (                  -- cola larga: checkout_started, filter_applied, page_view...
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id       UUID         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    session_id     UUID         NOT NULL,
    anonymous_id   UUID         NOT NULL,
    subject_key    UUID,
    event_name     VARCHAR(60)  NOT NULL REFERENCES tracking.event_definitions(event_name),
    surface        VARCHAR(20)  NOT NULL CHECK (surface IN ('MARKETPLACE','STORESUITE','OPERATIONS')),
    store_id       UUID,
    branch_id      UUID,
    product_id     UUID,
    order_id       UUID,
    schema_version SMALLINT     NOT NULL DEFAULT 1,
    properties     JSONB        NOT NULL DEFAULT '{}'::jsonb CHECK (pg_column_size(properties) <= 4096),
    PRIMARY KEY (occurred_at, id),
    CONSTRAINT uq_events__event UNIQUE (event_id, occurred_at)
) PARTITION BY RANGE (occurred_at);
CREATE INDEX brin_events__occurred_at ON tracking.events USING brin (occurred_at) WITH (pages_per_range = 32);
CREATE INDEX idx_events__name_occurred ON tracking.events (event_name, occurred_at);

CREATE TABLE tracking.identity_links (          -- une anonymous_id con subject_key al iniciar sesión
    anonymous_id    UUID        NOT NULL,
    subject_key     UUID        NOT NULL,
    first_linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_linked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (anonymous_id, subject_key)
);
CREATE INDEX idx_identity_links__subject_key ON tracking.identity_links (subject_key);
```

- **Las tres tablas de eventos son disjuntas:** cada evento va a una sola tabla, según `event_definitions.target_table`.
- **Hechos de negocio frente a comportamiento:** pedido creado, pago y envío **no** son eventos de tracking. Su fuente es el OLTP y sus historiales, escritos por el servidor y confiables. El tracking describe intención y exposición.
- **`occurred_at` del cliente:** si difiere de `received_at` en más de 24 h, el backend usa `received_at`.
- **Idempotencia:** `INSERT ... ON CONFLICT (event_id, occurred_at) DO NOTHING`.
- **`displayed_price` es lo que el cliente dice que mostró.** Para analizar la elasticidad se contrasta con `inventory_price_history` en `occurred_at`. Si difieren, gana el historial.
- **Demanda insatisfecha:**
  - búsquedas con `results_count = 0`;
  - `PRODUCT_VIEW` con `availability_shown = 'OUT_OF_STOCK'`;
  - búsquedas por vehículo con `compatible_results_count = 0`.

  Se agregan en `analytics.fact_unmet_demand_daily` (§5.11).
- **Carritos abandonados:** en fase 1 se derivan de los eventos `ADD_TO_CART` sin `checkout_completed` en 24 h. En fase 2 pasan a `carts` persistidos (§6.3).

### 5.10 Privacidad mínima (ver §8 para el modelo completo)

```sql
CREATE TABLE public.consent_purposes (
    code        VARCHAR(40)  PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    legal_basis VARCHAR(20)  NOT NULL CHECK (legal_basis IN ('CONTRACT','CONSENT','LEGAL_OBLIGATION','LEGITIMATE_INTEREST')),
    required    BOOLEAN      NOT NULL DEFAULT false,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Semilla: TERMS_OF_SERVICE (CONTRACT, required), PERSONAL_DATA_PROCESSING (CONSENT, required; autorización Ley 1581),
-- ANALYTICS_TRACKING, PERSONALIZATION, MARKETING_EMAIL, MARKETING_WHATSAPP, VEHICLE_DATA, ML_TRAINING (todas CONSENT).

CREATE TABLE public.person_consents (           -- solo inserción: cada cambio es una fila nueva (evidencia)
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    person_id      UUID         NOT NULL REFERENCES public.persons(id),
    purpose_code   VARCHAR(40)  NOT NULL REFERENCES public.consent_purposes(code),
    granted        BOOLEAN      NOT NULL,
    policy_version VARCHAR(20)  NOT NULL,
    channel        VARCHAR(20)  NOT NULL CHECK (channel IN ('WEB','APP','EMAIL','SUPPORT','IMPORT')),
    evidence       JSONB        NOT NULL DEFAULT '{}'::jsonb CHECK (pg_column_size(evidence) <= 2048),  -- hash del texto, pantalla
    ip_address     INET,                       -- evidencia de autorización; acceso restringido
    user_agent     VARCHAR(500),
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_person_consents__person_purpose ON public.person_consents (person_id, purpose_code, recorded_at DESC);

CREATE VIEW public.person_consents_current AS
SELECT DISTINCT ON (person_id, purpose_code) person_id, purpose_code, granted, policy_version, recorded_at
FROM public.person_consents ORDER BY person_id, purpose_code, recorded_at DESC;

CREATE TABLE privacy.subject_pseudonyms (
    person_id   UUID        PRIMARY KEY REFERENCES public.persons(id) ON DELETE CASCADE,
    subject_key UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 5.11 Capa analítica mínima (esquema `analytics`)

En la fase 1 se usan **tablas** pobladas por funciones SQL idempotentes, no vistas materializadas. La razón: `REFRESH MATERIALIZED VIEW` recalcula toda la historia cada noche, mientras que estas funciones recalculan una ventana (`DELETE` del rango + `INSERT ... SELECT` en una transacción). Las vistas materializadas se reservan para agregados pequeños (fase 2).

```sql
CREATE TABLE analytics.fact_sales_daily (
    sale_date        DATE          NOT NULL,   -- fecha local de orders.created_at (momento de la demanda)
    store_id         UUID          NOT NULL,
    branch_id        UUID          NOT NULL,
    product_id       UUID          NOT NULL,
    category_id      UUID,
    orders_count     INTEGER       NOT NULL,
    units_ordered    INTEGER       NOT NULL,
    units_cancelled  INTEGER       NOT NULL,
    units_delivered  INTEGER       NOT NULL,
    units_returned   INTEGER       NOT NULL DEFAULT 0,          -- fase 2 (devoluciones)
    gross_revenue    NUMERIC(16,2) NOT NULL,   -- Σ coalesce(list_unit_price, unit_price) × units
    discount_amount  NUMERIC(16,2) NOT NULL,
    net_revenue      NUMERIC(16,2) NOT NULL,   -- Σ unit_price × units de líneas no canceladas
    cogs             NUMERIC(16,4),            -- NULL si alguna línea no tiene costo
    avg_unit_price   NUMERIC(12,2),
    min_unit_price   NUMERIC(12,2),
    max_unit_price   NUMERIC(12,2),
    list_price_eod   NUMERIC(12,2),            -- de inventory_daily_snapshots
    promo_active     BOOLEAN,
    available_eod    INTEGER,
    stockout_minutes INTEGER,
    refreshed_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (sale_date, branch_id, product_id)
);
CREATE INDEX idx_fact_sales_daily__store_date ON analytics.fact_sales_daily (store_id, sale_date);
CREATE INDEX idx_fact_sales_daily__product_date ON analytics.fact_sales_daily (product_id, sale_date);

CREATE TABLE analytics.fact_order_fulfillment (   -- grano: venta (sale)
    sale_id                       UUID          PRIMARY KEY,
    order_id                      UUID          NOT NULL,
    store_id                      UUID          NOT NULL,
    branch_id                     UUID          NOT NULL,
    order_date                    DATE          NOT NULL,
    order_created_at              TIMESTAMPTZ   NOT NULL,
    confirmed_at                  TIMESTAMPTZ,
    ready_at                      TIMESTAMPTZ,
    shipped_at                    TIMESTAMPTZ,
    delivered_at                  TIMESTAMPTZ,
    cancelled_at                  TIMESTAMPTZ,
    final_status                  VARCHAR(50)   NOT NULL,
    cancellation_reason_code      VARCHAR(40),
    fulfillment_type              VARCHAR(20),
    carrier                       VARCHAR(100),
    origin_municipality_code      CHAR(5),
    destination_municipality_code CHAR(5),
    distance_km                   NUMERIC(8,2),
    hours_to_confirm              NUMERIC(10,2),
    hours_to_ready                NUMERIC(10,2),
    hours_to_ship                 NUMERIC(10,2),
    hours_in_transit              NUMERIC(10,2),
    hours_total                   NUMERIC(10,2),
    business_days_total           SMALLINT,     -- con analytics.dim_date.is_business_day_co
    promised_delivery_by          TIMESTAMPTZ,
    estimated_delivery_at         TIMESTAMPTZ,
    delivered_on_time             BOOLEAN,
    delivery_delay_hours          NUMERIC(10,2),
    units                         INTEGER       NOT NULL,
    net_total                     NUMERIC(12,2) NOT NULL,
    refreshed_at                  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_fact_order_fulfillment__store_date ON analytics.fact_order_fulfillment (store_id, order_date);

CREATE TABLE analytics.fact_unmet_demand_daily (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    demand_date         DATE         NOT NULL,
    signal_type         VARCHAR(25)  NOT NULL CHECK (signal_type IN ('ZERO_RESULTS','OUT_OF_STOCK_VIEW','NO_COMPATIBLE_RESULTS')),
    search_type         VARCHAR(20),
    query_hash          CHAR(64),
    query_text_sample   VARCHAR(200),  -- solo si distinct_sessions >= 3 (evita exponer consultas raras con PII)
    make_id             UUID,
    model_id            UUID,
    version_id          UUID,
    vehicle_year        SMALLINT,
    category_id         UUID,
    product_id          UUID,
    geo_department_code CHAR(2),
    events_count        INTEGER      NOT NULL,
    distinct_sessions   INTEGER      NOT NULL,
    refreshed_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_fact_unmet_demand_daily__grain UNIQUE NULLS NOT DISTINCT (demand_date, signal_type, search_type,
        query_hash, make_id, model_id, version_id, vehicle_year, category_id, product_id, geo_department_code)
);
CREATE INDEX idx_fact_unmet_demand_daily__date ON analytics.fact_unmet_demand_daily (demand_date);
```

Funciones de refresco. Todas `RETURNS integer` (filas escritas), son idempotentes y filtran `store.data_origin = 'REAL'` y `orders.data_origin = 'REAL'`:

- `analytics.refresh_fact_sales_daily(p_from date, p_to date)`.
- `analytics.refresh_fact_order_fulfillment(p_since timestamptz)`: recalcula las ventas con `sales.updated_at >= p_since` o con historial posterior a `p_since`.
- `analytics.refresh_fact_unmet_demand_daily(p_from date, p_to date)`.

Cadencia:

- **Cada noche**, tras el snapshot: `sales_daily` sobre los últimos 35 días (cubre cancelaciones y devoluciones tardías), `fulfillment` desde hace 48 h y `unmet_demand` sobre los últimos 3 días.
- **El día 1 de cada mes:** `sales_daily` sobre los últimos 13 meses.

**Tenancy:** las lecturas de paneles de almacén siempre filtran por `store_id` en el servidor. `analytics` no contiene PII.

---

## 6. Fase 2

### 6.1 Garaje del cliente y lecturas de kilometraje

```sql
-- Existente: versions (DB-VEH-1, aditivo)
ALTER TABLE public.versions
    ADD COLUMN year_from       SMALLINT    CHECK (year_from BETWEEN 1950 AND 2100),
    ADD COLUMN year_to         SMALLINT    CHECK (year_to BETWEEN 1950 AND 2100),
    ADD COLUMN engine_code     VARCHAR(30),
    ADD COLUMN displacement_cc INTEGER     CHECK (displacement_cc > 0),
    ADD COLUMN fuel_type       VARCHAR(20) CHECK (fuel_type IN ('GASOLINE','DIESEL','HYBRID','PLUG_IN_HYBRID','ELECTRIC','CNG','FLEX')),
    ADD COLUMN transmission    VARCHAR(20) CHECK (transmission IN ('MANUAL','AUTOMATIC','CVT','DCT','OTHER')),
    ADD COLUMN body_type       VARCHAR(30),
    ADD CONSTRAINT chk_versions__years CHECK (year_to IS NULL OR year_from IS NULL OR year_to >= year_from);

CREATE TABLE public.customer_vehicles (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id           UUID        NOT NULL REFERENCES public.persons(id),
    make_id             UUID        NOT NULL REFERENCES public.make(id),
    model_id            UUID        NOT NULL,
    version_id          UUID,
    model_year          SMALLINT    NOT NULL CHECK (model_year BETWEEN 1950 AND 2100),
    vin                 VARCHAR(17) CHECK (vin ~ '^[A-HJ-NPR-Z0-9]{17}$'),   -- opcional; dato personal
    nickname            VARCHAR(60),
    usage_type          VARCHAR(20) CHECK (usage_type IN ('PERSONAL','RIDE_HAILING','TAXI','FLEET','COMMERCIAL','OTHER')),
    fuel_type           VARCHAR(20),
    declared_monthly_km INTEGER     CHECK (declared_monthly_km >= 0),
    acquired_on         DATE,
    is_primary          BOOLEAN     NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_customer_vehicles__model_make    FOREIGN KEY (model_id, make_id)    REFERENCES public.model(id, make_id),
    CONSTRAINT fk_customer_vehicles__version_model FOREIGN KEY (version_id, model_id) REFERENCES public.versions(id, model_id)
);
CREATE INDEX idx_customer_vehicles__person ON public.customer_vehicles (person_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_customer_vehicles__model_year ON public.customer_vehicles (model_id, model_year);
CREATE UNIQUE INDEX uq_customer_vehicles__primary ON public.customer_vehicles (person_id) WHERE is_primary AND deleted_at IS NULL;
CREATE UNIQUE INDEX uq_customer_vehicles__person_vin ON public.customer_vehicles (person_id, vin) WHERE vin IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE public.vehicle_odometer_readings (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_vehicle_id UUID        NOT NULL REFERENCES public.customer_vehicles(id) ON DELETE CASCADE,
    odometer_km         INTEGER     NOT NULL CHECK (odometer_km BETWEEN 0 AND 3000000),
    read_at             TIMESTAMPTZ NOT NULL,
    source              VARCHAR(20) NOT NULL CHECK (source IN ('USER','CHECKOUT','SERVICE_RECORD','WARRANTY_CLAIM','OBD','IMPORT')),
    is_suspect          BOOLEAN     NOT NULL DEFAULT false,  -- menor que la lectura anterior o salto > 1000 km/día
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vehicle_odometer_readings__vehicle_read ON public.vehicle_odometer_readings (customer_vehicle_id, read_at DESC);

ALTER TABLE public.order_details
    ADD CONSTRAINT fk_order_details__customer_vehicle FOREIGN KEY (customer_vehicle_id) REFERENCES public.customer_vehicles(id);
```

- Que el cliente diga para qué vehículo compra (`order_details.customer_vehicle_id`) es **la etiqueta más valiosa** para tres modelos: compatibilidad, mantenimiento y demanda por vehículo.
- La placa no se guarda.

### 6.2 Evidencia de compatibilidad (requiere el cambio de mapeo H5)

```sql
-- Existente: products_compatibilies (tabla puente; aditivo)
ALTER TABLE public.products_compatibilies
    ADD COLUMN evidence_status         VARCHAR(20) NOT NULL DEFAULT 'SELLER_DECLARED'
               CHECK (evidence_status IN ('CONFIRMED','SELLER_DECLARED','SUGGESTED','REJECTED')),
    ADD COLUMN source                  VARCHAR(30) CHECK (source IN ('SELLER','OEM_CATALOG','SUPPLIER','AI_SUGGESTION','ADMIN','ERP')),
    ADD COLUMN year_from               SMALLINT,
    ADD COLUMN year_to                 SMALLINT,
    ADD COLUMN confirmed_by_account_id UUID REFERENCES public.accounts(id),
    ADD COLUMN confirmed_at            TIMESTAMPTZ,
    ADD COLUMN created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at              TIMESTAMPTZ NOT NULL DEFAULT now();
```

### 6.3 Carrito persistido y abandono

```sql
CREATE TABLE public.carts (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id          UUID        REFERENCES public.persons(id),
    anonymous_id       UUID,
    session_id         UUID,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','CONVERTED','ABANDONED','MERGED','EXPIRED')),
    currency           CHAR(3)     NOT NULL DEFAULT 'COP',
    converted_order_id UUID        REFERENCES public.orders(id),
    last_activity_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    abandoned_at       TIMESTAMPTZ,
    converted_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_carts__owner CHECK (person_id IS NOT NULL OR anonymous_id IS NOT NULL)
);
CREATE UNIQUE INDEX uq_carts__active_person ON public.carts (person_id) WHERE status = 'ACTIVE' AND person_id IS NOT NULL;
CREATE INDEX idx_carts__status_activity ON public.carts (status, last_activity_at);

CREATE TABLE public.cart_items (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id             UUID          NOT NULL REFERENCES public.carts(id),
    inventory_id        UUID          NOT NULL REFERENCES public.inventory(id),
    quantity            INTEGER       NOT NULL CHECK (quantity > 0),
    captured_unit_price NUMERIC(12,2) NOT NULL CHECK (captured_unit_price >= 0),
    customer_vehicle_id UUID          REFERENCES public.customer_vehicles(id),
    added_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    removed_at          TIMESTAMPTZ,  -- borrado lógico para conservar lo quitado
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_cart_items__cart_inventory_active ON public.cart_items (cart_id, inventory_id) WHERE removed_at IS NULL;
```

- **Abandono:** `CartAbandonmentJob` cada hora marca `ABANDONED` los carritos `ACTIVE` con artículos y `last_activity_at < now() - interval '24 hours'`. Es idempotente.
- Recordarle al cliente su carrito abandonado requiere `MARKETING_EMAIL`.

### 6.4 Reservas de stock

```sql
CREATE TABLE public.stock_reservations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id    UUID        NOT NULL REFERENCES public.inventory(id),
    order_detail_id UUID        REFERENCES public.order_details(id),
    cart_id         UUID        REFERENCES public.carts(id),
    quantity        INTEGER     NOT NULL CHECK (quantity > 0),
    status          VARCHAR(15) NOT NULL CHECK (status IN ('ACTIVE','CONSUMED','RELEASED','EXPIRED')),
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_stock_reservations__inventory_active ON public.stock_reservations (inventory_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_stock_reservations__expires ON public.stock_reservations (expires_at) WHERE status = 'ACTIVE';
```

`inventory.reserved_stock` debe ser igual a `Σ quantity ACTIVE` (se reconcilia en el job nocturno).

### 6.5 Proveedores, órdenes de compra y lead time real

```sql
CREATE TABLE public.suppliers (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id               UUID         NOT NULL REFERENCES public.store(id),
    name                   VARCHAR(200) NOT NULL,
    nit                    VARCHAR(20),
    contact_email          VARCHAR(200),
    contact_phone          VARCHAR(20),
    municipality_code      CHAR(5)      REFERENCES public.geo_municipalities(code),
    default_lead_time_days SMALLINT     CHECK (default_lead_time_days >= 0),
    payment_terms_days     SMALLINT     CHECK (payment_terms_days >= 0),
    active                 BOOLEAN      NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_suppliers__store_nit ON public.suppliers (store_id, nit) WHERE nit IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE public.supplier_products (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id              UUID          NOT NULL REFERENCES public.suppliers(id),
    product_id               UUID          NOT NULL REFERENCES public.products(id),
    supplier_sku             VARCHAR(100),
    last_unit_cost           NUMERIC(14,4) CHECK (last_unit_cost >= 0),
    min_order_qty            INTEGER       CHECK (min_order_qty > 0),
    pack_size                INTEGER       CHECK (pack_size > 0),
    declared_lead_time_days  SMALLINT      CHECK (declared_lead_time_days >= 0),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_supplier_products__supplier_product UNIQUE (supplier_id, product_id)
);

CREATE TABLE public.purchase_orders (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id              UUID          NOT NULL REFERENCES public.store(id),
    branch_id             UUID          NOT NULL REFERENCES public.branches(id),   -- destino
    supplier_id           UUID          NOT NULL REFERENCES public.suppliers(id),
    po_number             VARCHAR(40)   NOT NULL,
    status                VARCHAR(20)   NOT NULL CHECK (status IN ('DRAFT','SENT','CONFIRMED','PARTIALLY_RECEIVED',
                                        'RECEIVED','CANCELLED','CLOSED')),
    source                VARCHAR(20)   NOT NULL CHECK (source IN ('MANUAL','REORDER_SUGGESTION','ERP_SYNC')),
    prediction_id         BIGINT,       -- si vino de una sugerencia de reposición
    currency              CHAR(3)       NOT NULL DEFAULT 'COP',
    subtotal              NUMERIC(14,2) CHECK (subtotal >= 0),
    ordered_at            TIMESTAMPTZ,
    expected_at           TIMESTAMPTZ,  -- promesa del proveedor
    first_received_at     TIMESTAMPTZ,
    fully_received_at     TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    created_by_account_id UUID          REFERENCES public.accounts(id),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchase_orders__store_number UNIQUE (store_id, po_number)
);
CREATE INDEX idx_purchase_orders__branch_status ON public.purchase_orders (branch_id, status);
CREATE INDEX idx_purchase_orders__supplier ON public.purchase_orders (supplier_id);

CREATE TABLE public.purchase_order_items (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID          NOT NULL REFERENCES public.purchase_orders(id),
    product_id        UUID          NOT NULL REFERENCES public.products(id),
    inventory_id      UUID          REFERENCES public.inventory(id),
    qty_ordered       INTEGER       NOT NULL CHECK (qty_ordered > 0),
    qty_received      INTEGER       NOT NULL DEFAULT 0 CHECK (qty_received >= 0),
    unit_cost         NUMERIC(14,4) NOT NULL CHECK (unit_cost >= 0),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchase_order_items__po_product UNIQUE (purchase_order_id, product_id)
);

CREATE TABLE public.purchase_receipts (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id      UUID        NOT NULL REFERENCES public.purchase_orders(id),
    branch_id              UUID        NOT NULL REFERENCES public.branches(id),
    received_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_by_account_id UUID        REFERENCES public.accounts(id),
    supplier_document_ref  VARCHAR(60),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.purchase_receipt_items (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_receipt_id    UUID          NOT NULL REFERENCES public.purchase_receipts(id),
    purchase_order_item_id UUID          NOT NULL REFERENCES public.purchase_order_items(id),
    qty_received           INTEGER       NOT NULL CHECK (qty_received >= 0),
    qty_rejected           INTEGER       NOT NULL DEFAULT 0 CHECK (qty_rejected >= 0),
    unit_cost              NUMERIC(14,4) NOT NULL CHECK (unit_cost >= 0),
    inventory_movement_id  UUID          REFERENCES public.inventory_movements(id),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```

- **Lead time real** = `purchase_receipts.received_at − purchase_orders.ordered_at` por línea. Se expone en la vista `analytics.v_supplier_lead_times`, con `lead_time_days`, `promised_days = expected_at − ordered_at` y `fill_rate = qty_received / qty_ordered`.
- **En tránsito** = `Σ (qty_ordered − qty_received)` de las órdenes `SENT/CONFIRMED/PARTIALLY_RECEIVED`. A partir de aquí el snapshot rellena `in_transit` (deja de ser `NULL`).

### 6.6 Devoluciones y garantías con motivo tipificado

```sql
CREATE TABLE public.return_reasons (
    code               VARCHAR(40)  PRIMARY KEY,
    fault_party        VARCHAR(15)  NOT NULL CHECK (fault_party IN ('CUSTOMER','STORE','CARRIER','MANUFACTURER','PLATFORM','UNKNOWN')),
    applies_to         VARCHAR(10)  NOT NULL CHECK (applies_to IN ('RETURN','WARRANTY','BOTH')),
    is_fitment_related BOOLEAN      NOT NULL DEFAULT false,
    description        VARCHAR(255) NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Semilla: WRONG_FITMENT (UNKNOWN, BOTH, fitment=true), DEFECTIVE_ON_ARRIVAL (MANUFACTURER), FAILED_IN_USE (MANUFACTURER, WARRANTY),
-- DAMAGED_IN_TRANSIT (CARRIER), NOT_AS_DESCRIBED (STORE), WRONG_ITEM_SENT (STORE), MISSING_PARTS (STORE),
-- LATE_DELIVERY (CARRIER), NO_LONGER_NEEDED (CUSTOMER), ORDERED_BY_MISTAKE (CUSTOMER), FOUND_CHEAPER (CUSTOMER),
-- INSTALLATION_ISSUE (UNKNOWN), OTHER (UNKNOWN).

CREATE TABLE public.return_requests (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    request_number         BIGINT        GENERATED ALWAYS AS IDENTITY UNIQUE,
    order_id               UUID          NOT NULL REFERENCES public.orders(id),
    sale_id                UUID          NOT NULL REFERENCES public.sales(id),
    store_id               UUID          NOT NULL REFERENCES public.store(id),
    person_id              UUID          NOT NULL REFERENCES public.persons(id),
    request_type           VARCHAR(10)   NOT NULL CHECK (request_type IN ('RETURN','WARRANTY','EXCHANGE')),
    reason_code            VARCHAR(40)   NOT NULL REFERENCES public.return_reasons(code),
    status                 VARCHAR(20)   NOT NULL CHECK (status IN ('REQUESTED','APPROVED','REJECTED','IN_TRANSIT',
                                         'RECEIVED','INSPECTED','RESOLVED','CANCELLED')),
    resolution             VARCHAR(20)   CHECK (resolution IN ('REFUND','PARTIAL_REFUND','REPLACEMENT','REPAIR','STORE_CREDIT','REJECTED')),
    refund_amount          NUMERIC(12,2) CHECK (refund_amount >= 0),
    customer_vehicle_id    UUID          REFERENCES public.customer_vehicles(id),
    odometer_km_at_failure INTEGER       CHECK (odometer_km_at_failure >= 0),
    installed_on           DATE,
    failed_on              DATE,
    customer_comment       TEXT,         -- posible PII; nunca se copia a analytics
    claim_id               UUID          REFERENCES public.claims(id),
    requested_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    approved_at            TIMESTAMPTZ,
    received_at            TIMESTAMPTZ,
    resolved_at            TIMESTAMPTZ,
    resolved_by_account_id UUID          REFERENCES public.accounts(id),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_return_requests__sale ON public.return_requests (sale_id);
CREATE INDEX idx_return_requests__store_status ON public.return_requests (store_id, status);

CREATE TABLE public.return_request_items (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    return_request_id     UUID        NOT NULL REFERENCES public.return_requests(id),
    order_detail_id       UUID        NOT NULL REFERENCES public.order_details(id),
    units                 INTEGER     NOT NULL CHECK (units > 0),
    item_condition        VARCHAR(20) CHECK (item_condition IN ('SEALED','OPENED','INSTALLED','DAMAGED','DEFECTIVE')),
    restocked             BOOLEAN     NOT NULL DEFAULT false,
    inventory_movement_id UUID        REFERENCES public.inventory_movements(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_return_request_items__request_detail UNIQUE (return_request_id, order_detail_id)
);

CREATE TABLE public.return_status_history (   -- misma forma que sale_status_history, sin reason_code
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_request_id UUID        NOT NULL REFERENCES public.return_requests(id),
    from_status       VARCHAR(20),
    to_status         VARCHAR(20) NOT NULL,
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type        VARCHAR(10) NOT NULL CHECK (actor_type IN ('CUSTOMER','STORE','PLATFORM','SYSTEM','PROVIDER')),
    actor_account_id  UUID        REFERENCES public.accounts(id),
    notes             VARCHAR(255)
);
CREATE INDEX idx_return_status_history__request ON public.return_status_history (return_request_id, changed_at);
```

`WRONG_FITMENT` cruzado con `order_details.compatibility_status_shown` da la etiqueta de la tasa de error real de cada nivel de evidencia de compatibilidad.

### 6.7 Riesgo de pago y fraude

```sql
-- Existente: payments (DB-BIL-3, aditivo)
ALTER TABLE public.payments
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN attempt_number  SMALLINT      NOT NULL DEFAULT 1 CHECK (attempt_number >= 1),
    ADD COLUMN environment     VARCHAR(10)   NOT NULL DEFAULT 'SANDBOX' CHECK (environment IN ('SANDBOX','PRODUCTION')),
    ADD COLUMN failure_code    VARCHAR(50),
    ADD COLUMN failure_message VARCHAR(255),
    ADD COLUMN failed_at       TIMESTAMPTZ,
    ADD COLUMN refunded_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0);
CREATE UNIQUE INDEX uq_payments__idempotency_key ON public.payments (idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE public.payment_risk_assessments (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id             UUID         NOT NULL REFERENCES public.payments(id),
    order_id               UUID         NOT NULL REFERENCES public.orders(id),
    assessed_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    stage                  VARCHAR(20)  NOT NULL CHECK (stage IN ('PRE_AUTHORIZATION','POST_AUTHORIZATION','MANUAL_REVIEW')),
    risk_score             NUMERIC(6,5) CHECK (risk_score BETWEEN 0 AND 1),
    provider_risk_score    NUMERIC(6,5) CHECK (provider_risk_score BETWEEN 0 AND 1),
    decision               VARCHAR(10)  NOT NULL CHECK (decision IN ('ALLOW','REVIEW','DENY')),
    rules_triggered        TEXT[]       NOT NULL DEFAULT '{}',
    signals                JSONB        NOT NULL DEFAULT '{}'::jsonb CHECK (pg_column_size(signals) <= 4096),
    model_version_id       UUID         REFERENCES ml.model_versions(id),
    ip_address             INET,        -- seguridad; el job de retención lo pone a NULL a los 180 días
    device_hash            CHAR(64),    -- HMAC-SHA256 con secreto del backend
    outcome                VARCHAR(20)  CHECK (outcome IN ('LEGIT','FRAUD_CONFIRMED','CHARGEBACK','FRIENDLY_FRAUD','UNKNOWN')),
    outcome_at             TIMESTAMPTZ,
    outcome_source         VARCHAR(20)  CHECK (outcome_source IN ('PROVIDER','MANUAL_REVIEW','CHARGEBACK_NOTICE','TIMEOUT')),
    reviewed_by_account_id UUID         REFERENCES public.accounts(id)
);
CREATE INDEX idx_payment_risk_assessments__payment ON public.payment_risk_assessments (payment_id);
CREATE INDEX brin_payment_risk_assessments__assessed_at ON public.payment_risk_assessments USING brin (assessed_at);
```

- Claves permitidas en `signals`: `account_age_days`, `is_first_order`, `orders_last_24h`, `failed_payments_last_24h`, `login_failures_last_24h`, `distinct_shipping_addresses_30d`, `ip_department_matches_shipping`, `email_domain_disposable`, `order_total_vs_account_avg`, `high_value_category_share`.
- Un pago con `environment = 'SANDBOX'` nunca entra en un dataset de entrenamiento real.

### 6.8 Esquema `ml`: registro, feature store y predicciones

```sql
CREATE TABLE ml.models (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(80)  NOT NULL UNIQUE,     -- demand_forecast_daily, reorder_point, delivery_eta, payment_fraud...
    task          VARCHAR(20)  NOT NULL CHECK (task IN ('FORECAST','CLASSIFICATION','REGRESSION','RANKING','ANOMALY')),
    target_entity VARCHAR(20)  NOT NULL CHECK (target_entity IN ('PRODUCT_BRANCH','CUSTOMER','BRANCH','PAYMENT','SALE','VEHICLE')),
    description   VARCHAR(500) NOT NULL,
    owner_module  VARCHAR(60)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ml.datasets (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code               VARCHAR(80)  NOT NULL,
    version            VARCHAR(40)  NOT NULL,
    description        VARCHAR(500),
    definition_sql_sha CHAR(64)     NOT NULL,     -- hash de la consulta que lo generó
    as_of_cutoff       TIMESTAMPTZ  NOT NULL,     -- corte temporal (sin datos futuros)
    window_from        DATE         NOT NULL,
    window_to          DATE         NOT NULL,
    row_count          BIGINT       CHECK (row_count >= 0),
    storage_uri        VARCHAR(500),               -- objeto en almacenamiento, no en la BD
    data_origin        VARCHAR(10)  NOT NULL CHECK (data_origin IN ('REAL','SYNTHETIC','MIXED')),
    is_pseudonymized   BOOLEAN      NOT NULL DEFAULT true,
    contains_direct_pii BOOLEAN     NOT NULL DEFAULT false CHECK (contains_direct_pii = false),
    created_by         VARCHAR(100) NOT NULL,      -- job o persona (sin email)
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_datasets__code_version UNIQUE (code, version)
);

CREATE TABLE ml.model_versions (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id            UUID         NOT NULL REFERENCES ml.models(id),
    version             VARCHAR(40)  NOT NULL,
    algorithm           VARCHAR(60)  NOT NULL,   -- SEASONAL_NAIVE, MOVING_AVERAGE, CROSTON, LIGHTGBM, RULES...
    is_baseline         BOOLEAN      NOT NULL DEFAULT false,
    training_dataset_id UUID         REFERENCES ml.datasets(id),
    feature_set_version SMALLINT,
    hyperparameters     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    metrics             JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- WAPE, MAE, RMSE, AUC... global y por segmento
    baseline_metrics    JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- obligatorio antes de DEPLOYED
    known_limitations   TEXT,
    train_from          DATE,
    train_to            DATE,
    validation_from     DATE,
    validation_to       DATE,
    artifact_uri        VARCHAR(500),
    code_commit         CHAR(40),
    data_origin         VARCHAR(10)  NOT NULL CHECK (data_origin IN ('REAL','SYNTHETIC','MIXED')),
    status              VARCHAR(15)  NOT NULL CHECK (status IN ('TRAINED','VALIDATED','DEPLOYED','RETIRED','REJECTED')),
    role                VARCHAR(12)  CHECK (role IN ('CHAMPION','CHALLENGER','SHADOW')),
    trained_at          TIMESTAMPTZ,
    deployed_at         TIMESTAMPTZ,
    retired_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_model_versions__model_version UNIQUE (model_id, version),
    CONSTRAINT chk_model_versions__deploy_needs_baseline CHECK (status <> 'DEPLOYED' OR is_baseline OR baseline_metrics <> '{}'::jsonb),
    CONSTRAINT chk_model_versions__synthetic_not_champion CHECK (NOT (data_origin <> 'REAL' AND role = 'CHAMPION'))
);
CREATE UNIQUE INDEX uq_model_versions__one_champion ON ml.model_versions (model_id) WHERE role = 'CHAMPION' AND status = 'DEPLOYED';

ALTER TABLE public.inventory_reorder_policies   -- columna creada en fase 1 (§5.4)
    ADD CONSTRAINT fk_inventory_reorder_policies__model_version FOREIGN KEY (model_version_id) REFERENCES ml.model_versions(id);

CREATE TABLE ml.batch_runs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_type         VARCHAR(20)  NOT NULL CHECK (run_type IN ('FEATURE_BUILD','TRAINING','BACKTEST','SCORING','OUTCOME_MATCH')),
    model_version_id UUID         REFERENCES ml.model_versions(id),
    idempotency_key  VARCHAR(120) NOT NULL UNIQUE,     -- p. ej. 'FEATURE_BUILD:product_branch:2026-10-13'
    parameters       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status           VARCHAR(15)  NOT NULL CHECK (status IN ('RUNNING','SUCCEEDED','FAILED')),
    rows_written     BIGINT,
    error_message    VARCHAR(500),                      -- seguro, sin datos personales
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ
);

CREATE TABLE ml.feature_definitions (
    feature_set   VARCHAR(40)  NOT NULL,   -- product_branch, customer, branch
    version       SMALLINT     NOT NULL,
    feature_name  VARCHAR(80)  NOT NULL,
    data_type     VARCHAR(20)  NOT NULL,
    description   VARCHAR(500) NOT NULL,
    lineage       TEXT         NOT NULL,   -- tablas/columnas de origen y ventana
    introduced_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (feature_set, version, feature_name)
);

-- Regla de corte (point-in-time): features con as_of_date = D usan SOLO datos con fecha local < D.
CREATE TABLE ml.product_branch_features (
    as_of_date                DATE          NOT NULL,
    branch_id                 UUID          NOT NULL,
    product_id                UUID          NOT NULL,
    store_id                  UUID          NOT NULL,
    category_id               UUID,
    feature_set_version       SMALLINT      NOT NULL,
    units_7d                  INTEGER,
    units_28d                 INTEGER,
    units_91d                 INTEGER,
    units_365d                INTEGER,
    units_same_28d_last_year  INTEGER,
    days_with_sales_28d       SMALLINT,
    revenue_28d               NUMERIC(16,2),
    avg_price_28d             NUMERIC(12,2),
    price_change_pct_28d      NUMERIC(9,4),
    promo_days_28d            SMALLINT,
    stockout_days_28d         SMALLINT,
    on_hand                   INTEGER,
    available                 INTEGER,
    in_transit                INTEGER,
    days_of_supply            NUMERIC(10,2),
    views_28d                 INTEGER,
    add_to_cart_28d           INTEGER,
    unmet_searches_28d        INTEGER,      -- búsquedas sin resultado que coinciden con vehículos compatibles
    compatible_versions_count INTEGER,
    lead_time_days_avg        NUMERIC(8,2),
    computed_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (as_of_date, branch_id, product_id)
) PARTITION BY RANGE (as_of_date);

CREATE TABLE ml.customer_features (            -- cadencia semanal (lunes); sin person_id
    as_of_date                DATE          NOT NULL,
    subject_key               UUID          NOT NULL,
    feature_set_version       SMALLINT      NOT NULL,
    tenure_days               INTEGER,
    recency_days              INTEGER,
    frequency_90d             INTEGER,
    frequency_365d            INTEGER,
    monetary_365d             NUMERIC(16,2),
    avg_order_value_365d      NUMERIC(12,2),
    distinct_categories_365d  SMALLINT,
    returns_rate_365d         NUMERIC(6,5),
    cancellations_365d        SMALLINT,
    vehicles_count            SMALLINT,
    primary_vehicle_age_years SMALLINT,
    last_odometer_km          INTEGER,
    est_monthly_km            INTEGER,
    home_department_code      CHAR(2),       -- geografía gruesa
    rfm_segment               VARCHAR(30),
    computed_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (as_of_date, subject_key)
) PARTITION BY RANGE (as_of_date);

CREATE TABLE ml.branch_features (
    as_of_date             DATE         NOT NULL,
    branch_id              UUID         NOT NULL,
    store_id               UUID         NOT NULL,
    feature_set_version    SMALLINT     NOT NULL,
    active_skus            INTEGER,
    orders_28d             INTEGER,
    fill_rate_28d          NUMERIC(6,5),
    avg_hours_to_ready_28d NUMERIC(10,2),
    on_time_rate_28d       NUMERIC(6,5),
    cancellation_rate_28d  NUMERIC(6,5),
    store_rating_avg_90d   NUMERIC(3,2),
    return_rate_90d        NUMERIC(6,5),
    municipality_code      CHAR(5),
    computed_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (as_of_date, branch_id)
);

CREATE TABLE ml.predictions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    predicted_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    model_version_id UUID          NOT NULL REFERENCES ml.model_versions(id),
    batch_run_id     UUID          REFERENCES ml.batch_runs(id),
    entity_type      VARCHAR(20)   NOT NULL CHECK (entity_type IN ('PRODUCT_BRANCH','CUSTOMER','BRANCH','PAYMENT','SALE','VEHICLE')),
    store_id         UUID,
    branch_id        UUID,
    product_id       UUID,
    subject_key      UUID,
    payment_id       UUID,
    sale_id          UUID,
    customer_vehicle_id UUID,
    features_as_of   DATE,
    target_date      DATE,
    horizon_days     SMALLINT      CHECK (horizon_days >= 0),
    value            NUMERIC(18,6),
    lower_bound      NUMERIC(18,6),  -- p10
    upper_bound      NUMERIC(18,6),  -- p90
    predicted_class  VARCHAR(50),
    probability      NUMERIC(6,5)  CHECK (probability BETWEEN 0 AND 1),
    explanation      JSONB         NOT NULL DEFAULT '{}'::jsonb CHECK (pg_column_size(explanation) <= 4096),
    PRIMARY KEY (predicted_at, id)
) PARTITION BY RANGE (predicted_at);
CREATE INDEX brin_predictions__predicted_at ON ml.predictions USING brin (predicted_at);
CREATE INDEX idx_predictions__model_target  ON ml.predictions (model_version_id, target_date);
CREATE INDEX idx_predictions__branch_product ON ml.predictions (branch_id, product_id, target_date) WHERE product_id IS NOT NULL;

CREATE TABLE ml.prediction_outcomes (
    prediction_id    BIGINT        NOT NULL,
    predicted_at     TIMESTAMPTZ   NOT NULL,
    actual_value     NUMERIC(18,6),
    actual_class     VARCHAR(50),
    observed_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    abs_error        NUMERIC(18,6),
    censored         BOOLEAN       NOT NULL DEFAULT false,  -- p. ej. hubo quiebre de stock en target_date
    PRIMARY KEY (prediction_id, predicted_at),
    CONSTRAINT fk_prediction_outcomes__prediction FOREIGN KEY (predicted_at, prediction_id)
        REFERENCES ml.predictions (predicted_at, id)
);

CREATE VIEW ml.v_current_demand_forecast AS  -- lo que consume storesuite (filtrar por store_id en servidor)
SELECT p.* FROM ml.predictions p
JOIN ml.model_versions mv ON mv.id = p.model_version_id AND mv.role = 'CHAMPION' AND mv.status = 'DEPLOYED'
JOIN ml.models m ON m.id = mv.model_id AND m.code = 'demand_forecast_daily'
WHERE p.predicted_at >= now() - interval '2 days';
```

### 6.9 Analítica fase 2

| Objeto | Tipo | Grano y contenido | Refresco |
|---|---|---|---|
| `analytics.fact_vehicle_demand_daily` | tabla + función | `(demand_date, make_id, model_id, version_id, vehicle_year, category_id, department_code)`: `searches`, `zero_result_searches`, `product_views`, `add_to_carts`, `units_sold_explicit` (líneas con `customer_vehicle_id`), `units_sold_allocated NUMERIC(14,4)` (unidades repartidas por igual entre las versiones compatibles del producto; se documenta que es una estimación) | nocturno, últimos 3 días |
| `analytics.mv_inventory_rotation_monthly` | vista materializada (índice único `(month, branch_id, product_id)`, `REFRESH CONCURRENTLY`) | `units_sold`, `avg_on_hand`, `turnover = units_sold / NULLIF(avg_on_hand, 0)`, `days_of_supply`, `stockout_days`, `days_since_last_sale`, `is_dead_stock` (sin ventas en 180 días con `on_hand > 0`) | nocturno |
| `analytics.mv_customer_cohorts` | vista materializada | `(cohort_month, months_since_first)`: `active_subjects`, `orders`, `net_revenue` (por `subject_key`) | semanal |
| `analytics.mv_customer_rfm` | vista materializada | `subject_key`, `recency_days`, `frequency_365d`, `monetary_365d`, `r_score`/`f_score`/`m_score` (quintiles 1-5) y `segment`. Es el estado actual; el histórico vive en `ml.customer_features` | semanal |
| `analytics.fact_search_funnel_daily` | tabla + función | `(date, surface, search_type, device_type)`: `searches`, `zero_results`, `clicks`, `add_to_carts`, `checkouts`, `orders` (unidos por `session_id`) | nocturno |
| `analytics.v_supplier_lead_times` | vista | §6.5 | en línea |
| `analytics.fact_returns_daily` | tabla + función | `(date, store_id, product_id, reason_code, fault_party, compatibility_status_shown)`: `units`, `refund_amount` | nocturno |

---

## 7. Fase 3 (contemplado; no se detalla hasta que haya un caso de uso)

| Tema | Tablas | Nota |
|---|---|---|
| Historial de mantenimiento | `vehicle_service_records` (vehículo, fecha, km, tipo de servicio, taller, `order_detail_id` instalado), `maintenance_schedules` (intervalo **documentado** con fuente), `maintenance_reminders` | No se inventan intervalos (blueprint §5.7) |
| Transferencias y conteos | `inventory_transfers`/`_items`, `stock_counts`/`_items` | Generan movimientos `TRANSFER_*` y `COUNT_ADJUSTMENT` |
| Disputas y contracargos | `payment_disputes` | Etiqueta `CHARGEBACK` para el modelo de fraude |
| Uso de IA | `ai_interactions` (función, proveedor, modelo, versión del prompt, latencia, tokens, costo estimado, feedback, `subject_key`) | Blueprint §11.1; el texto libre no se guarda por defecto |
| Datos externos | `analytics.fx_rates_daily` (TRM del Banco de la República; autopartes importadas), parque automotor por departamento (RUNT/DANE) | **Requiere aprobación**: servicio o dataset externo; verificar licencia |
| Embeddings | columna `vector` para búsqueda semántica | **Requiere aprobación**: extensión `pgvector` |
| Endurecimiento de PII | mover `persons`, `person_addresses` y el VIN a un esquema `pii` y/o cifrado por columna | **Requiere aprobación**: cambio destructivo y de seguridad |
| RLS por `store_id` en `analytics` y `ml` | políticas | **Requiere aprobación**: cambio de seguridad |
| Particionar `inventory_movements` | recrear la tabla como particionada | **Requiere aprobación**: destructivo; solo si supera unos 50 M de filas |

---

## 8. Privacidad (Ley 1581 de 2012, Decreto 1377 de 2013)

**Inventario de datos personales y dónde pueden aparecer:**

| Dato | Tablas OLTP | ¿Puede ir a `tracking`/`analytics`/`ml`? |
|---|---|---|
| Nombre, identificación, fecha de nacimiento, teléfono | `persons` | **No.** En ML solo una banda de edad (`<25`, `25-34`...) calculada en la vista de exportación |
| Email, contraseña | `accounts` | No |
| Dirección, coordenadas | `locations`, `person_addresses`, `order_addresses` | Solo `municipality_code` y `department_code` |
| VIN | `customer_vehicles.vin` | Solo `vin_wmi` y `vin_model_year_code` |
| IP | `login_attempts`, `account_sessions`, `person_consents`, `payment_risk_assessments` | **No.** En tracking solo la geografía derivada; la IP nunca se guarda |
| Texto libre (comentarios, reclamos, notas) | `store_reviews.comment`, `claims.description`, `return_requests.customer_comment` | No (para minería de texto habrá un proceso aparte y aprobado) |
| Consultas de búsqueda | `tracking.search_events.query_text` | Sí, depuradas en el servidor (se eliminan patrones de email y teléfono móvil `3\d{9}`/`+57`, y los VIN de 17 caracteres se reducen a WMI) |

**Mecanismos:**

1. **Seudonimización:** `privacy.subject_pseudonyms` asigna a cada persona un `subject_key` **aleatorio**, no derivado de su id, así que no se puede revertir sin la tabla. Todo lo que está fuera de `public` usa solo `subject_key`.
2. **Supresión y revocación (habeas data):**
   - Se borra la fila de `subject_pseudonyms` y las de `tracking.identity_links` de ese `subject_key`.
   - A partir de ahí los datos de `tracking`, `analytics` y `ml` quedan desvinculados de la persona.
   - El OLTP sigue su propio proceso de supresión, que conserva los registros comerciales que exige la ley.
   - Si se revoca `ANALYTICS_TRACKING`, el `subject_key` deja de adjuntarse a nuevos eventos.
   - Si se revoca `ML_TRAINING`, los jobs de construcción de datasets excluyen ese `subject_key`, filtrando con `person_consents_current` vía `subject_pseudonyms`.
3. **Consentimiento previo:**
   - Sin `ANALYTICS_TRACKING`, el backend rechaza los eventos de tracking con `202` y los descarta sin guardarlos, para no filtrar el estado del consentimiento.
   - Los visitantes anónimos guardan su elección en el navegador. El backend solo acepta el lote si trae `consent: { analytics: true }`, y la elección se registra como evento `analytics_consent_granted`.
4. **Datasets de entrenamiento:**
   - Se generan solo desde vistas `ml.v_training_*` que no exponen PII.
   - `ml.datasets.contains_direct_pii` está restringido a `false`.
   - En exportaciones fuera de la BD se suprimen los grupos con menos de 5 sujetos.
5. **Separación por roles** (§4; requiere aprobación). El rol analista no puede leer `public` ni `privacy`.
6. **Pendiente legal:** el texto de las finalidades (`consent_purposes`) y los plazos de §9 deben validarse con asesoría jurídica. Esto es un supuesto, no una decisión técnica.

---

## 9. Retención

| Datos | Retención | Mecanismo |
|---|---|---|
| `tracking.*` (eventos y sesiones) | 25 meses (dos ciclos anuales para estacionalidad) | `RetentionJob`: `detach_and_drop_partitions_older_than(..., 25)` |
| `tracking.identity_links` | Mientras exista `subject_key`; 25 meses sin uso | `DELETE` por `last_linked_at` |
| `inventory_daily_snapshots` | 36 meses | eliminación de particiones |
| `ml.product_branch_features`, `ml.customer_features` | 24 meses | eliminación de particiones |
| `ml.predictions` + `prediction_outcomes` | 36 meses | eliminación de particiones (borrar antes los resultados de esas particiones) |
| `payment_risk_assessments.ip_address` | 180 días | `UPDATE ... SET ip_address = NULL` (excepción documentada al modo de solo inserción) |
| Historiales de pedido, venta, envío y pago, ledger, precios, devoluciones | Indefinida (como mínimo 10 años: registros comerciales) | — |
| `person_consents` | Mientras dure la relación + 5 años (validar con asesoría legal) | — |
| `analytics.fact_*` | Indefinida (agregados sin PII) | — |

**Requiere aprobación:** activar `RetentionJob`, porque elimina datos. Se aprueba una vez la política y, a partir de ahí, el job la ejecuta. Mientras no se apruebe, el job solo registra qué borraría (modo `dry-run`).

---

## 10. Plan de migraciones (archivos para el agente `database`)

Todos en `database/migrations/`, cada `V` con su `U`. El timestamp definitivo se toma al crear el archivo (ADR 0001 §2.2), **conservando este orden**.

**Fase 0 (ADR 0001)**

1. `V202610140900__baseline_schema.sql`

**Fase 1**

2. `V202610140910__schemas_and_conventions.sql`: esquemas `tracking`, `analytics`, `ml` y `privacy`; funciones `forbid_update_delete`, `ensure_monthly_partitions` y `detach_and_drop_partitions_older_than`.
3. `V202610140920__geo_reference.sql` (§5.1) + `R__seed_geo_divipola.sql`.
4. `V202610140930__calendar_dimension.sql` (§5.2) + `R__seed_holidays_co.sql`.
5. `V202610140940__inventory_ledger_reasons.sql` (§5.3). Las restricciones `chk_inventory_movements__type`/`__balance` van en un archivo aparte, `V202610140945__inventory_movements_constraints.sql`, para poder aprobarlas por separado.
6. `V202610140950__inventory_price_history.sql` (§5.5, con relleno `INITIAL_LOAD`).
7. `V202610141000__inventory_reorder_policies.sql` (§5.4).
8. `V202610141010__inventory_daily_snapshots.sql` (§5.6 + particiones iniciales).
9. `V202610141020__order_status_history.sql` (§5.7, con relleno inicial del historial).
10. `V202610141030__privacy_consents_pseudonyms.sql` (§5.10).
11. `V202610141040__tracking_events.sql` (§5.9 + particiones iniciales + semilla de `event_definitions`).
12. `V202610141050__order_attribution.sql` (§5.8).
13. `V202610141100__analytics_facts_phase1.sql` (§5.11 + funciones de refresco).
14. *(Requiere aprobación)* `V202610141110__timestamptz_existing_tables.sql` (ADR 0001 §2.5).
15. *(Requiere aprobación; contracción posterior)* `V2026xxxxxxxx__contract_phase1_not_null.sql`: `inventory_movements.reason_code` y `order_details.product_id` pasan a `NOT NULL` tras verificar que no quedan nulos.

**Fase 2 (orden)**

1. `customer_garage`
2. `compatibility_evidence`
3. `carts`
4. `stock_reservations`
5. `purchasing`
6. `returns`
7. `ml_registry_and_features`
8. `payment_risk` (depende de `ml.model_versions`)
9. `analytics_phase2`

Cada `U` de la fase 1 que borra tablas nuevas con datos empieza con el guard `autopartco.allow_destructive` (ADR 0001 §2.2, regla 3).

## 11. Cambios que requieren aprobación

| # | Cambio | Motivo |
|---|---|---|
| A1 | Dependencia Flyway + recurso en `pom.xml` | nueva dependencia |
| A2 | Recrear la BD de `backend/.env` desde la línea base | comando destructivo contra una BD |
| A3 | `TIMESTAMP → TIMESTAMPTZ` en las tablas existentes | **cambio de tipo**, reescritura de tablas |
| A4 | `CHECK` nuevos en `inventory_movements` (`movement_type IN ('IN','OUT')`, balance) | restricción nueva sobre tabla existente (hoy vacía) |
| A5 | Contracción `NOT NULL` en `inventory_movements.reason_code` y `order_details.product_id` | endurecimiento sobre datos existentes |
| A6 | Roles de BD y `GRANT` por esquema | cambio de seguridad |
| A7 | Endpoint público `POST /api/v1/tracking/events` | cambio de seguridad (superficie pública, limitación de frecuencia) |
| A8 | Secreto HMAC para `device_hash` y geolocalización de IP (por ejemplo, base GeoLite: licencia y dependencia) | secreto nuevo, dependencia o dato externo |
| A9 | Activar `RetentionJob` (elimina particiones y anula IP) | borrado de datos |
| A10 | Semilla DIVIPOLA desde datos del DANE | verificar licencia de datos externos |
| A11 | `@EnableScheduling` en `shared/config` | el área `shared/config` requiere confirmación (U5) |
| — | **No se proponen** renombres (`store`, `bussnies_*`, `products_compatibilies`) ni el cambio de `products.sku` a único por almacén | se dejan fuera; requerirían aprobación |

---

## 12. Impacto en backend

Reglas transversales:

- Las filas de historial, ledger y precio se escriben **en la misma transacción** que el cambio de estado, mediante una llamada directa dentro de `core` (diseño modular §4.5). No se usan eventos `AFTER_COMMIT`, que no son fiables porque no hay outbox.
- Los efectos secundarios (notificaciones, alertas de stock) sí van por evento `AFTER_COMMIT`.
- Los jobs usan `@Scheduled` (requiere A11) y `pg_try_advisory_lock(hashtext('<job>'))` para no ejecutarse dos veces con varias instancias (sin ShedLock). Todos son idempotentes, registran inicio, fin y filas, y nunca guardan PII en los logs.

| Tabla(s) | Módulo dueño | Entidad / acceso | Servicio y cambio | Evento emitido |
|---|---|---|---|---|
| `inventory_movements`, `inventory_movement_reasons`, `inventory.reserved_stock/unit_cost/last_movement_at` | `core.inventory` | `internal/InventoryMovement` (JPA, inmutable) o JDBC; `Inventory` gana campos | **Nuevo `StockLedger.record(inventoryId, reasonCode, direction, qty, unitCost, referenceType, referenceId, actorAccountId, idempotencyKey)`**: `SELECT ... FOR UPDATE` de `inventory`, inserta el movimiento y actualiza `stock` y `last_movement_at`. `StockService.updateListing` deja de hacer `setStock` y calcula el delta (`MANUAL_CORRECTION`/`COUNT_ADJUSTMENT`). `createListing` registra `INITIAL_LOAD`. Repetir la `idempotencyKey` devuelve el movimiento existente | `StockMoved(inventoryId, branchId, productId, newStock, reasonCode)` → `storesuite.stockalerts` |
| `inventory_price_history` | `core.inventory` (el precio vive en `inventory`) | `internal/InventoryPriceRecord` | **Nuevo `ListingPrices.change(inventoryId, listPrice, unitCost, vatRate, source, actor, reason)`**: cierra la fila abierta e inserta la nueva. Lo usan `updateListing` (si cambia el precio), las importaciones CSV/ERP y la recepción de compras (costo) | `ListingPriceChanged` |
| `inventory_reorder_policies` | `core.inventory` | `internal/ReorderPolicy` | `ReorderPolicies.set(...)` (cierra e inserta) y `current(inventoryId)` | — |
| `inventory_daily_snapshots` | `core.inventory` | JDBC (`INSERT ... SELECT`) | **`InventorySnapshotJob`** a las 00:15 Bogotá: escribe los snapshots del día anterior, reconcilia `stock` con el ledger y `reserved_stock` con las reservas, y registra discrepancias en `audit_logs` | — |
| `order_status_history`, `sale_status_history`, `cancellation_reasons`, columnas nuevas de `sales`/`orders` | `core.order` | JDBC (como hoy) | `SaleStatusService.updateSaleStatus` inserta en `sale_status_history` y fija `confirmed_at`, `ready_at` o `cancelled_at`. `SaleStatusRequest` gana `cancellationReasonCode` (opcional, obligatorio si `status = CANCELLED`). Errores nuevos: `SELLER_ORDER_CANCELLATION_REASON_REQUIRED` (400) y `SELLER_ORDER_CANCELLATION_REASON_INVALID` (400). Una venta cancelada genera `SALE_CANCELLED_RESTOCK` vía `StockLedger` cuando haya reservas o descuento de stock | `SaleStatusChanged(saleId, storeId, from, to, reasonCode)` (ya previsto en el diseño modular) |
| `order_details` (instantánea), `orders` (totales, canal, `data_origin`, `session_id`), `order_attributions` | `core.order` (checkout de `marketplace.checkout`) | JDBC | El checkout, cuando exista, rellena la instantánea con `ListingQueries` + `PriceCalculator`: `list_unit_price`, `discount_amount`, `promotion_id`, `unit_cost_snapshot` y `compatibility_status_shown`. Inserta `order_status_history(NULL→PENDING)` y `order_attributions` desde la sesión de tracking (solo con consentimiento). Las salidas de stock van por `StockLedger` con `MARKETPLACE_SALE` | `OrderPlaced` |
| `shipments` / `shipment_events` (columnas nuevas) | `core.order` | JDBC | `ShipmentService` fija `from_status_id`, `source`, `actor_account_id`, `estimated_delivery_at` (en `ShipmentCreateRequest`, opcional) y los municipios de origen (sucursal) y destino (`order_addresses`), además de `distance_km` (haversine) | `ShipmentStatusChanged` |
| `payment_events` (columnas nuevas); fase 2: `payments.*`, `payment_risk_assessments` | `core.billing` | JDBC/JPA | Al procesar el webhook se rellenan `provider_event_id`, `provider_status`, `source` y `from_status_id`; la unicidad hace idempotente el reintento. Fase 2: `PaymentRiskService.assess(...)` antes de autorizar, `environment` según la configuración y `outcome` desde la revisión manual o el contracargo | `PaymentStatusChanged` |
| `geo_*`, `locations.municipality_code`, `order_addresses.municipality_code` | `shared.location` | `Location` gana `municipalityCode` | Resolución del municipio al crear direcciones y sucursales (selector DIVIPOLA en el frontend). Job de relleno por coincidencia de nombre, que registra los casos ambiguos | — |
| `consent_purposes`, `person_consents`, `privacy.subject_pseudonyms` | `core.identity` | `internal/PersonConsent` (inmutable), `internal/SubjectPseudonym` | `Consents.record(personId, purpose, granted, policyVersion, channel, ip, ua)`, `Consents.current(personId)` y `SubjectPseudonyms.subjectKeyFor(personId)` (lo crea si falta). El registro de cuenta exige `TERMS_OF_SERVICE` y `PERSONAL_DATA_PROCESSING`. Endpoints `GET /api/v1/me/consents` y `PUT /api/v1/me/consents/{purposeCode}`. Flujo de supresión (operations) | `ConsentChanged(subjectKey, purpose, granted)` |
| `tracking.*` | **nuevo `core.tracking`** | JDBC por lotes | `POST /api/v1/tracking/events` (A7): lote de hasta 50 eventos y 64 KB; responde `202 Accepted`; límite de frecuencia por `anonymous_id` + IP. Valida contra `event_definitions` (debe tener `pii_reviewed = true`), depura `query_text`, deriva `device_type`, upsert de la sesión, resuelve `subject_key` si hay JWT y aplica `ON CONFLICT DO NOTHING`. Errores: `TRACKING_BATCH_TOO_LARGE` (413) y `TRACKING_EVENT_INVALID` (400, con detalle por índice). Las búsquedas del marketplace hechas por el servidor (`ProductBrowseController`) pueden registrar `search_events` directamente, con `results_count` y `latency_ms` reales, mientras el cliente solo envía clics | — |
| `analytics.*` | **nuevo `core.analytics`** | JDBC (llama a funciones SQL) | `AnalyticsRefreshJob` (nocturno, tras el snapshot; mensual). `SalesAnalyticsQueries`, `FulfillmentQueries` y `UnmetDemandQueries` para `storesuite.analytics` y `operations.metrics`, **siempre con `store_id`** del `StoreAccess` | — |
| Particiones y retención | `shared.persistence` | JDBC | `PartitionMaintenanceJob` (diario: `ensure_monthly_partitions(..., 3)` y alerta si la `default` tiene filas) y `RetentionJob` (A9; `dry-run` por defecto) | — |
| Fase 2: `customer_vehicles`, `vehicle_odometer_readings` | `core.vehicle` (o `core.identity` si se considera dato del cliente; recomendado `core.vehicle` con `personId` por R1) | JPA | `Garage` (CRUD con propiedad por `personId`, lectura de kilometraje con `is_suspect` calculado) para `intelligence.vehiclehistory` y `marketplace.vehiclesearch` | `OdometerRecorded` |
| Fase 2: `products_compatibilies` con evidencia | `core.catalog` | **Cambiar `@ElementCollection` por la entidad `internal/ProductFitment`** antes de la migración (H5) | `ProductFitments.declare/confirm/reject` | — |
| Fase 2: `carts`, `cart_items`, `stock_reservations` | `core.order` (carrito) + `core.inventory` (reservas) | JPA | `marketplace.cart`; `StockReservations.reserve/consume/release`; `CartAbandonmentJob` | `CartAbandoned` |
| Fase 2: `suppliers`, `purchase_*` | **nuevo `core.purchasing`** (o dentro de `core.inventory`) | JPA | `storesuite.purchasing`; la recepción llama a `StockLedger.record(PURCHASE_RECEIPT)` y a `ListingPrices.change(..., PURCHASE_RECEIPT)` si cambia el costo | `PurchaseReceived` |
| Fase 2: `return_*` | `core.order` | JPA | `marketplace.customercare` (solicitud) y `storesuite.orders` (resolución); la reposición llama a `StockLedger.record(CUSTOMER_RETURN)` | `ReturnRequested`, `ReturnResolved` |
| Fase 2: `ml.*` | **nuevo `core.ml`** | JDBC | `ModelRegistry`, `FeatureBuildJob` (diario `product_branch`/`branch`, semanal `customer`), `PredictionWriter`, `PredictionOutcomeJob` (une predicción y realidad; marca `censored` si hubo quiebre), y la lectura `DemandForecastQueries` para `intelligence.demandforecast`. El entrenamiento se hace fuera (Colab o script reproducible) y registra `model_versions` + `datasets` | — |

Modulith/ArchUnit: dar de alta `core.tracking`, `core.analytics` y `core.ml` (y `core.purchasing` en la fase 2) en `allowedDependencies`. Las lecturas SQL entre dominios siguen permitidas solo en clases `*Queries` (decisión 12 del diseño modular).

---

## 13. Orden de implementación por agente y archivos

Secuencia estricta `database` → `backend` → `frontend` → `qa-seguridad` por bloque. Ningún archivo lo tocan dos agentes.

**Bloque 0: Flyway (ADR 0001).** Requiere A1 y A2.

- `database`: `database/migrations/V202610140900__baseline_schema.sql` + `U...`; encabezado de obsolescencia en `database/init.sql`.
- `backend`: `backend/pom.xml`, `backend/src/main/resources/application.yaml`, `application-dev.yaml`; arranque de humo con `validate`.
- `qa-seguridad`: verificación del ADR 0001 §4.

**Bloque 1: captura OLTP (prioridad máxima).**

- `database`: migraciones 2 a 10 y 12 de §10 (14 y 15 solo con aprobación).
- `backend`:
  - `core/inventory/`: `StockLedger`, `ListingPrices`, `ReorderPolicies`, `InventorySnapshotJob`, `internal/*`.
  - `core/order/`: `SaleStatusService`, `SaleStatusRequest`, `ShipmentService`, `ShipmentCreateRequest`.
  - `core/billing/` (webhook → `payment_events`).
  - `core/identity/` (consentimientos, seudónimos, `/me/consents`).
  - `shared/location/Location.java`.
  - `shared/persistence/PartitionMaintenanceJob`.
  - `shared/config/` (`@EnableScheduling`, requiere A11).
  - Pruebas en `backend/src/test/...` para cada servicio, incluido el IDOR por `store_id`.
- `frontend`: selector de municipio DIVIPOLA en las direcciones; motivo de cancelación en el cambio de estado de venta; pantalla de consentimientos.
- `qa-seguridad`:
  - el ledger cuadra con el stock;
  - el historial se escribe en todas las transiciones;
  - los motivos inválidos se rechazan;
  - el costo (`unit_cost`) no aparece en las respuestas del marketplace;
  - los consentimientos son de solo inserción.

**Bloque 2: tracking y analítica fase 1.** Requiere A6 (opcional en desarrollo) y A7.

- `database`: migraciones 11 y 13.
- `backend`: `core/tracking/`, `core/analytics/`, `marketplace/catalog/ProductBrowseController` (registro de búsquedas en servidor) y `shared/persistence/RetentionJob` (`dry-run`).
- `frontend`: `frontend/src/lib/tracking/`:
  - `anonymous_id` en `localStorage`;
  - sesión de 30 min de inactividad;
  - lote con `navigator.sendBeacon`;
  - banner de consentimiento;
  - lectura de UTM en la entrada.
- `qa-seguridad`:
  - abuso del endpoint (tamaño, frecuencia, `event_name` no catalogado);
  - sin consentimiento no se guarda nada;
  - consulta a `information_schema` que comprueba que `tracking`, `analytics` y `ml` no tienen columnas `email`, `identification`, `name`, `surname`, `number_phone`, `vin`, `ip_address` ni `address_line`;
  - los paneles filtran por `store_id`;
  - comprobación de H6 (Supabase Data API).

**Bloque 3 (fase 2):**

- garaje + evidencia de compatibilidad (el `backend` cambia antes el mapeo H5);
- carrito y reservas;
- compras;
- devoluciones;
- `ml`;
- riesgo de pago;
- analítica fase 2.

Cada sub-bloque sigue la misma secuencia de cuatro agentes.

## 14. Abierto para los implementadores

- `database`: confirmar que `UNIQUE NULLS NOT DISTINCT` está disponible (PostgreSQL ≥ 15; el objetivo es 16+) y que la instancia de Supabase es 16 o superior.
- `database`: decidir si los rellenos (`INITIAL_LOAD`, historial inicial, `order_details.product_id`) van en la misma `V` o en una `V` de datos separada. Recomendado: separada, para poder revertir la estructura sin datos.
- `backend`: confirmar el artefacto de Flyway para Spring Boot 4.1 (ADR 0001 §2.1) y el comportamiento de Community ante los archivos `U`.
- `backend`: definir la máquina de estados completa de `sales` (hoy solo `PENDING → CONFIRMED|CANCELLED` y `CONFIRMED → PREPARING|CANCELLED`). El historial admite cualquier código de `states`, pero las transiciones válidas son una decisión de negocio pendiente (blueprint §5.6).
- Producto: confirmar si el mercado objetivo incluye México. Por ahora se siembra solo Colombia.
