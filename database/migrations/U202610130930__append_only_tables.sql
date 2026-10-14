-- =============================================================================
-- U202610130930__append_only_tables.sql  (rollback de V202610130930)
-- Quita los triggers de solo inserción de las tablas existentes. Mantiene
-- forbid_update_delete() si alguna tabla nueva la usa todavía (los U de las
-- fases predictivas la retiran en orden). Sin pérdida de datos.
-- psql -v ON_ERROR_STOP=1 --single-transaction -f <archivo>
-- =============================================================================

SET LOCAL lock_timeout = '5s';

DROP TRIGGER IF EXISTS trg_audit_logs_append_only ON public.audit_logs;
DROP TRIGGER IF EXISTS trg_audit_logs_append_only_truncate ON public.audit_logs;
DROP TRIGGER IF EXISTS trg_login_attempts_append_only ON public.login_attempts;
DROP TRIGGER IF EXISTS trg_login_attempts_append_only_truncate ON public.login_attempts;
DROP TRIGGER IF EXISTS trg_payment_events_append_only ON public.payment_events;
DROP TRIGGER IF EXISTS trg_payment_events_append_only_truncate ON public.payment_events;
DROP TRIGGER IF EXISTS trg_shipment_events_append_only ON public.shipment_events;
DROP TRIGGER IF EXISTS trg_shipment_events_append_only_truncate ON public.shipment_events;
DROP TRIGGER IF EXISTS trg_inventory_movements_append_only ON public.inventory_movements;
DROP TRIGGER IF EXISTS trg_inventory_movements_append_only_truncate ON public.inventory_movements;

-- Sin CASCADE: falla si quedan triggers de otras tablas que la usan.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger t JOIN pg_proc p ON p.oid = t.tgfoid
        WHERE p.proname = 'forbid_update_delete'
    ) THEN
        DROP FUNCTION IF EXISTS public.forbid_update_delete();
    END IF;
END $$;
