-- =============================================================================
-- V202610140940__inventory_ledger_reasons.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.3 (corrige H1)
--
-- Propósito
--   - Catálogo public.inventory_movement_reasons con semilla (el código
--     depende de ella, por eso va en V con ON CONFLICT DO NOTHING).
--   - inventory_movements: columnas de motivo, origen, actor, referencia,
--     costo, idempotencia y occurred_at. Todas son nullables o tienen DEFAULT.
--     reason_code pasa a NOT NULL solo en la contracción (A5, pending-approval).
--   - inventory: reserved_stock (DEFAULT 0), unit_cost, last_movement_at,
--     last_counted_at. Inventory.java no las mapea y los INSERT/UPDATE de
--     Hibernate no las tocan.
--   - Los CHECK movement_type IN ('IN','OUT') y de balance son A4 y están en
--     pending-approval/V202610140945.
--   - unit_cost es dato sensible: nunca debe salir en respuestas del
--     marketplace (lo verifica qa-seguridad).
--
-- Transacción: SÍ. Bloqueos: ACCESS EXCLUSIVE breve en inventory e
--   inventory_movements (ADD COLUMN con DEFAULT constante o now(), sin
--   reescritura en PG 11+). chk_inventory__reserved se crea NOT VALID y se
--   valida aquí mismo: todas las filas tienen reserved_stock = 0.
--   Los índices de inventory_movements se crean sin CONCURRENTLY porque hoy la
--   tabla está vacía (ningún código la escribe).
-- Rollback: U202610140940__inventory_ledger_reasons.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.inventory_movement_reasons (
    code                VARCHAR(40)  NOT NULL,
    direction           VARCHAR(3)   NOT NULL,
    counts_as_demand    BOOLEAN      NOT NULL,
    counts_as_shrinkage BOOLEAN      NOT NULL DEFAULT false,
    requires_reference  BOOLEAN      NOT NULL DEFAULT false,
    description         VARCHAR(255) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_inventory_movement_reasons PRIMARY KEY (code),
    CONSTRAINT chk_inventory_movement_reasons__direction CHECK (direction IN ('IN', 'OUT', 'ANY')),
    CONSTRAINT chk_inventory_movement_reasons__code CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE OR REPLACE TRIGGER trg_inventory_movement_reasons_updated_at
    BEFORE UPDATE ON public.inventory_movement_reasons FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

INSERT INTO public.inventory_movement_reasons
    (code, direction, counts_as_demand, counts_as_shrinkage, requires_reference, description)
VALUES
    ('INITIAL_LOAD',           'IN',  false, false, false, 'Carga inicial de existencias'),
    ('PURCHASE_RECEIPT',       'IN',  false, false, true,  'Recepción de compra a proveedor'),
    ('MARKETPLACE_SALE',       'OUT', true,  false, true,  'Venta en el marketplace'),
    ('POS_SALE',               'OUT', true,  false, false, 'Venta en mostrador o informada por ERP'),
    ('SALE_CANCELLED_RESTOCK', 'IN',  false, false, true,  'Reingreso por venta cancelada'),
    ('CUSTOMER_RETURN',        'IN',  false, false, true,  'Devolución del cliente'),
    ('RETURN_TO_SUPPLIER',     'OUT', false, false, false, 'Devolución al proveedor'),
    ('TRANSFER_OUT',           'OUT', false, false, true,  'Salida por traslado entre sucursales'),
    ('TRANSFER_IN',            'IN',  false, false, true,  'Entrada por traslado entre sucursales'),
    ('COUNT_ADJUSTMENT',       'ANY', false, true,  false, 'Ajuste por conteo físico'),
    ('DAMAGE',                 'OUT', false, true,  false, 'Avería'),
    ('LOSS_THEFT',             'OUT', false, true,  false, 'Pérdida o hurto'),
    ('OBSOLESCENCE',           'OUT', false, true,  false, 'Obsolescencia'),
    ('ERP_SYNC',               'ANY', false, false, true,  'Sincronización con ERP'),
    ('CSV_IMPORT',             'ANY', false, false, true,  'Importación CSV'),
    ('MANUAL_CORRECTION',      'ANY', false, false, false, 'Corrección manual de existencias')
ON CONFLICT (code) DO NOTHING;

-- inventory_movements (existente; aditivo) ------------------------------------------
ALTER TABLE public.inventory_movements
    ADD COLUMN IF NOT EXISTS reason_code      VARCHAR(40),
    ADD COLUMN IF NOT EXISTS source           VARCHAR(10),
    ADD COLUMN IF NOT EXISTS actor_account_id UUID,
    ADD COLUMN IF NOT EXISTS reference_type   VARCHAR(30),
    ADD COLUMN IF NOT EXISTS reference_id     UUID,
    ADD COLUMN IF NOT EXISTS unit_cost        NUMERIC(14,4),
    ADD COLUMN IF NOT EXISTS occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS idempotency_key  VARCHAR(120),
    ADD COLUMN IF NOT EXISTS notes            VARCHAR(255);

CREATE OR REPLACE FUNCTION pg_temp.add_constraint(p_table regclass, p_name TEXT, p_ddl TEXT)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = p_table AND conname = p_name) THEN
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', p_table, p_name, p_ddl);
    END IF;
END;
$$;

SELECT pg_temp.add_constraint('public.inventory_movements', 'fk_inventory_movements__reason_code',
    'FOREIGN KEY (reason_code) REFERENCES public.inventory_movement_reasons (code) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.inventory_movements', 'fk_inventory_movements__actor_account_id',
    'FOREIGN KEY (actor_account_id) REFERENCES public.accounts (id) ON DELETE SET NULL');
SELECT pg_temp.add_constraint('public.inventory_movements', 'chk_inventory_movements__source',
    $c$CHECK (source IN ('USER', 'ORDER', 'ERP', 'CSV', 'JOB', 'SYSTEM'))$c$);
SELECT pg_temp.add_constraint('public.inventory_movements', 'chk_inventory_movements__reference_type',
    $c$CHECK (reference_type IN ('ORDER_DETAIL', 'PURCHASE_RECEIPT_ITEM', 'TRANSFER_ITEM', 'RETURN_ITEM',
                                 'SYNC_RUN', 'IMPORT_JOB', 'STOCK_COUNT'))$c$);
SELECT pg_temp.add_constraint('public.inventory_movements', 'chk_inventory_movements__reference_pair',
    'CHECK ((reference_type IS NULL) = (reference_id IS NULL))');
SELECT pg_temp.add_constraint('public.inventory_movements', 'chk_inventory_movements__unit_cost',
    'CHECK (unit_cost >= 0)');

-- ON DELETE SET NULL de actor_account_id debe poder modificar la fila de solo
-- inserción: se recrea el trigger con esa excepción.
CREATE OR REPLACE TRIGGER trg_inventory_movements_append_only
    BEFORE UPDATE OR DELETE ON public.inventory_movements
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('actor_account_id:null');

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_movements__idempotency_key
    ON public.inventory_movements (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_movements__inventory_occurred
    ON public.inventory_movements (inventory_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_movements__reference
    ON public.inventory_movements (reference_type, reference_id) WHERE reference_type IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_movements__reason
    ON public.inventory_movements (reason_code);
CREATE INDEX IF NOT EXISTS idx_inventory_movements__actor
    ON public.inventory_movements (actor_account_id) WHERE actor_account_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS brin_inventory_movements__occurred_at
    ON public.inventory_movements USING brin (occurred_at) WITH (pages_per_range = 32);

-- inventory (existente; aditivo) ---------------------------------------------------------
ALTER TABLE public.inventory
    ADD COLUMN IF NOT EXISTS reserved_stock   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unit_cost        NUMERIC(14,4),
    ADD COLUMN IF NOT EXISTS last_movement_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_counted_at  TIMESTAMPTZ;

SELECT pg_temp.add_constraint('public.inventory', 'chk_inventory__unit_cost',
    'CHECK (unit_cost >= 0) NOT VALID');
SELECT pg_temp.add_constraint('public.inventory', 'chk_inventory__reserved',
    'CHECK (reserved_stock >= 0 AND reserved_stock <= stock) NOT VALID');
ALTER TABLE public.inventory VALIDATE CONSTRAINT chk_inventory__unit_cost;
ALTER TABLE public.inventory VALIDATE CONSTRAINT chk_inventory__reserved;

DROP FUNCTION pg_temp.add_constraint(regclass, TEXT, TEXT);

COMMENT ON COLUMN public.inventory.unit_cost IS 'Costo vigente (promedio ponderado). Sensible: nunca exponer en el marketplace.';
COMMENT ON COLUMN public.inventory_movements.reason_code IS 'Motivo tipificado. NOT NULL en la contracción A5.';
