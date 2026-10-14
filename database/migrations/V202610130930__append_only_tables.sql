-- =============================================================================
-- V202610130930__append_only_tables.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito: tablas de solo inserción, sin UPDATE, DELETE ni TRUNCATE.
--   Función public.forbid_update_delete() (nombre del diseño predictivo §3).
--   La reutilizan las tablas nuevas de las fases predictivas.
--
-- Tablas: audit_logs, login_attempts, payment_events, shipment_events,
--   inventory_movements.
-- Comprobado contra el backend:
--   - AuditService solo hace repository.save() de entidades nuevas (INSERT).
--     AuditLog tiene @SQLDelete (UPDATE ... SET deleted_at), pero ningún
--     código lo invoca. Si se invocara, ahora fallaría. Es lo esperado: un log
--     de auditoría no se borra.
--   - LoginAttemptService solo inserta.
--   - ShipmentService solo inserta en shipment_events.
--   - payment_events e inventory_movements no se escriben hoy.
--
-- Excepciones (argumentos del trigger):
--   'col'       -> la columna puede cambiar (columnas de resultado, p. ej. outcome)
--   'col:null'  -> la columna solo puede pasar a NULL (anonimización o
--                  ON DELETE SET NULL de la FK)
--   audit_logs usa 'account_id:null' (V202610130940 pone ON DELETE SET NULL).
-- Purga: DELETE y TRUNCATE solo se permiten si la sesión fija
--   SET LOCAL autopartco.allow_purge = 'on'. Lo usará el job de retención,
--   que requiere aprobación (A9). Esta variable NO es una barrera de
--   seguridad: cualquier sesión puede fijarla. La barrera real es revocar
--   UPDATE/DELETE al rol de la aplicación (pending-approval, A6).
--
-- Transacción: SÍ. Rollback: U202610130930__append_only_tables.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE OR REPLACE FUNCTION public.forbid_update_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    old_row   JSONB;
    new_row   JSONB;
    col       TEXT;
    arg       TEXT;
    only_null BOOLEAN;
    allowed   BOOLEAN;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        IF coalesce(current_setting('autopartco.allow_purge', true), '') = 'on' THEN
            RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NULL END;
        END IF;
        RAISE EXCEPTION 'append-only table %: % no permitido', TG_TABLE_NAME, TG_OP
            USING ERRCODE = '42501';
    END IF;

    -- UPDATE: cada columna que cambia debe estar en la lista de excepciones.
    -- Se excluyen las columnas generadas: en un trigger BEFORE UPDATE su valor
    -- en NEW todavía no está recalculado y daría falsos positivos.
    old_row := to_jsonb(OLD);
    new_row := to_jsonb(NEW);
    FOR col IN
        SELECT n.key FROM jsonb_each(new_row) n
        WHERE n.value IS DISTINCT FROM old_row -> n.key
          AND NOT EXISTS (
              SELECT 1 FROM pg_attribute a
              WHERE a.attrelid = TG_RELID AND a.attname = n.key AND a.attgenerated <> ''
          )
    LOOP
        allowed := false;
        FOREACH arg IN ARRAY coalesce(TG_ARGV, ARRAY[]::TEXT[]) LOOP
            only_null := arg LIKE '%:null';
            IF split_part(arg, ':', 1) = col
               AND (NOT only_null OR new_row -> col = 'null'::jsonb) THEN
                allowed := true;
                EXIT;
            END IF;
        END LOOP;
        IF NOT allowed THEN
            RAISE EXCEPTION 'append-only table %: no se puede modificar la columna %', TG_TABLE_NAME, col
                USING ERRCODE = '42501';
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.forbid_update_delete() IS
    'Tablas de solo inserción. Args: columnas modificables (col) o que solo pueden pasar a NULL (col:null). Purga con autopartco.allow_purge=on.';

-- audit_logs ---------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON public.audit_logs
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('account_id:null');
CREATE OR REPLACE TRIGGER trg_audit_logs_append_only_truncate
    BEFORE TRUNCATE ON public.audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

-- login_attempts -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_login_attempts_append_only
    BEFORE UPDATE OR DELETE ON public.login_attempts
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();
CREATE OR REPLACE TRIGGER trg_login_attempts_append_only_truncate
    BEFORE TRUNCATE ON public.login_attempts
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

-- payment_events -------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_payment_events_append_only
    BEFORE UPDATE OR DELETE ON public.payment_events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();
CREATE OR REPLACE TRIGGER trg_payment_events_append_only_truncate
    BEFORE TRUNCATE ON public.payment_events
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

-- shipment_events ------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_shipment_events_append_only
    BEFORE UPDATE OR DELETE ON public.shipment_events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();
CREATE OR REPLACE TRIGGER trg_shipment_events_append_only_truncate
    BEFORE TRUNCATE ON public.shipment_events
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

-- inventory_movements --------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_inventory_movements_append_only
    BEFORE UPDATE OR DELETE ON public.inventory_movements
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();
CREATE OR REPLACE TRIGGER trg_inventory_movements_append_only_truncate
    BEFORE TRUNCATE ON public.inventory_movements
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();
