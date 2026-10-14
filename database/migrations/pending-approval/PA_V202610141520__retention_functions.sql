-- REQUIERE APROBACIÓN (A9): borrado de datos por retención
-- =============================================================================
-- V202610141520__retention_functions.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §3, §9 / §11 A9
--
-- Qué hace: funciones que BORRAN datos según la política de retención. Crear
--   las funciones no borra nada. Lo que se aprueba es activar RetentionJob,
--   que las invoca. Mientras no se apruebe, el job usa p_dry_run = true (valor
--   por defecto) y solo informa de lo que borraría.
--   public.detach_and_drop_partitions_older_than(parent, keep_months, dry_run)
--       Quita y elimina las particiones <tabla>_pYYYYMM cuyo rango termina
--       antes de date_trunc('month', now()) - keep_months. Nunca toca la
--       default.
--       tracking.* 25 meses; inventory_daily_snapshots 36; ml features 24;
--       ml.predictions 36 (borra antes los prediction_outcomes de esas
--       particiones).
--   public.purge_payment_risk_ip(days, dry_run)
--       ip_address = NULL a los 180 días (excepción documentada al modo de
--       solo inserción).
--   public.purge_stale_identity_links(months, dry_run)
--       DELETE de vínculos sin uso en 25 meses.
--   Los plazos deben validarse con asesoría legal (§8.6).
-- Al aprobar: muévalo a database/migrations/ con un timestamp nuevo.
-- Transacción: SÍ. Rollback: U202610141520__retention_functions.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';

CREATE OR REPLACE FUNCTION public.detach_and_drop_partitions_older_than(
    p_parent regclass, p_keep_months INT, p_dry_run BOOLEAN DEFAULT true
)
RETURNS INT
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    cutoff  DATE := (date_trunc('month', now() AT TIME ZONE 'America/Bogota') - make_interval(months => p_keep_months))::date;
    part    RECORD;
    dropped INT := 0;
BEGIN
    IF p_keep_months < 1 THEN
        RAISE EXCEPTION 'p_keep_months debe ser >= 1';
    END IF;

    FOR part IN
        SELECT c.oid::regclass AS part_rel, c.relname,
               to_date(substring(c.relname FROM '_p([0-9]{6})$'), 'YYYYMM') AS month_from
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = p_parent
          AND c.relname ~ '_p[0-9]{6}$'
    LOOP
        IF (part.month_from + interval '1 month')::date <= cutoff THEN
            IF p_dry_run THEN
                RAISE NOTICE '[dry-run] se eliminaría la partición % (desde %)', part.part_rel, part.month_from;
            ELSE
                IF p_parent = 'ml.predictions'::regclass THEN
                    DELETE FROM ml.prediction_outcomes
                    WHERE predicted_at >= part.month_from::timestamp AT TIME ZONE 'UTC'
                      AND predicted_at <  (part.month_from + interval '1 month')::timestamp AT TIME ZONE 'UTC';
                END IF;
                EXECUTE format('ALTER TABLE %s DETACH PARTITION %s', p_parent, part.part_rel);
                EXECUTE format('DROP TABLE %s', part.part_rel);
            END IF;
            dropped := dropped + 1;
        END IF;
    END LOOP;
    RETURN dropped;
END;
$$;

CREATE OR REPLACE FUNCTION public.purge_payment_risk_ip(p_days INT DEFAULT 180, p_dry_run BOOLEAN DEFAULT true)
RETURNS BIGINT
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    n BIGINT;
BEGIN
    IF p_dry_run THEN
        SELECT count(*) INTO n FROM payment_risk_assessments
        WHERE ip_address IS NOT NULL AND assessed_at < now() - make_interval(days => p_days);
        RETURN n;
    END IF;
    UPDATE payment_risk_assessments SET ip_address = NULL
    WHERE ip_address IS NOT NULL AND assessed_at < now() - make_interval(days => p_days);
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

CREATE OR REPLACE FUNCTION public.purge_stale_identity_links(p_months INT DEFAULT 25, p_dry_run BOOLEAN DEFAULT true)
RETURNS BIGINT
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    n BIGINT;
BEGIN
    IF p_dry_run THEN
        SELECT count(*) INTO n FROM tracking.identity_links
        WHERE last_linked_at < now() - make_interval(months => p_months);
        RETURN n;
    END IF;
    DELETE FROM tracking.identity_links WHERE last_linked_at < now() - make_interval(months => p_months);
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

COMMENT ON FUNCTION public.detach_and_drop_partitions_older_than(regclass, INT, BOOLEAN) IS
    'DESTRUCTIVA (A9). dry_run por defecto. Elimina particiones mensuales anteriores al corte; nunca la default.';
