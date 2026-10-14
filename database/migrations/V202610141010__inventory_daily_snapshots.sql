-- =============================================================================
-- V202610141010__inventory_daily_snapshots.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.6
--
-- Propósito: serie diaria densa por inventario (incluye días sin ventas y con
--   quiebre) para el modelo de demanda con censura explícita:
--   available = on_hand - reserved, stockout_minutes 0-1440,
--   in_transit NULL = no medido (hasta que existan compras).
--   Particionada por mes (snapshot_date) con partición default. Sin FK, por
--   volumen y para permitir purgas por partición. La integridad la garantiza
--   InventorySnapshotJob (00:15 America/Bogota; INSERT ... ON CONFLICT DO
--   UPDATE, idempotente).
--   Crea las particiones del mes actual y de los 3 siguientes. El backend
--   (PartitionMaintenanceJob) llama cada día a
--   ensure_monthly_partitions(..., 3) y alerta si la default tiene filas.
--   Retención 36 meses (A9, pending-approval).
--
-- Transacción: SÍ. Rollback: U202610141010__inventory_daily_snapshots.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.inventory_daily_snapshots (
    snapshot_date    DATE          NOT NULL,
    inventory_id     UUID          NOT NULL,
    store_id         UUID          NOT NULL,
    branch_id        UUID          NOT NULL,
    product_id       UUID          NOT NULL,
    on_hand          INTEGER       NOT NULL,
    reserved         INTEGER       NOT NULL,
    available        INTEGER       GENERATED ALWAYS AS (on_hand - reserved) STORED,
    in_transit       INTEGER,
    min_on_hand      INTEGER,
    stockout_minutes INTEGER,
    list_price       NUMERIC(12,2) NOT NULL,
    effective_price  NUMERIC(12,2),
    unit_cost        NUMERIC(14,4),
    is_published     BOOLEAN       NOT NULL,
    reorder_point    INTEGER,
    units_in         INTEGER       NOT NULL DEFAULT 0,
    units_out        INTEGER       NOT NULL DEFAULT 0,
    units_sold       INTEGER       NOT NULL DEFAULT 0,
    captured_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_inventory_daily_snapshots PRIMARY KEY (snapshot_date, inventory_id),
    CONSTRAINT chk_inventory_daily_snapshots__reserved CHECK (reserved >= 0),
    CONSTRAINT chk_inventory_daily_snapshots__in_transit CHECK (in_transit >= 0),
    CONSTRAINT chk_inventory_daily_snapshots__stockout_minutes CHECK (stockout_minutes BETWEEN 0 AND 1440),
    CONSTRAINT chk_inventory_daily_snapshots__prices CHECK (list_price >= 0 AND effective_price >= 0 AND unit_cost >= 0),
    CONSTRAINT chk_inventory_daily_snapshots__units CHECK (units_in >= 0 AND units_out >= 0 AND units_sold >= 0
                                                           AND units_sold <= units_out)
) PARTITION BY RANGE (snapshot_date);

CREATE TABLE IF NOT EXISTS public.inventory_daily_snapshots_default
    PARTITION OF public.inventory_daily_snapshots DEFAULT;

CREATE INDEX IF NOT EXISTS idx_inventory_daily_snapshots__branch_product
    ON public.inventory_daily_snapshots (branch_id, product_id, snapshot_date);
CREATE INDEX IF NOT EXISTS idx_inventory_daily_snapshots__store_date
    ON public.inventory_daily_snapshots (store_id, snapshot_date);

SELECT public.ensure_monthly_partitions('public.inventory_daily_snapshots',
                                        public.bogota_date(now()), 4);

COMMENT ON TABLE public.inventory_daily_snapshots IS
    'Snapshot diario por inventario (día local Bogotá cerrado). Sin FK. Retención 36 meses (A9).';
