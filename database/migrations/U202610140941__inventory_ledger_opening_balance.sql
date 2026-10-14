-- =============================================================================
-- U202610140941__inventory_ledger_opening_balance.sql  (rollback de V202610140941)
-- DESTRUCTIVO: requiere aprobación. Borra los movimientos de saldo inicial
-- (idempotency_key 'OPENING_BALANCE:%'). Usa la purga controlada de la tabla
-- de solo inserción.
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL autopartco.allow_purge = 'on';

DELETE FROM public.inventory_movements
WHERE idempotency_key LIKE 'OPENING\_BALANCE:%'
  AND reason_code = 'INITIAL_LOAD';
