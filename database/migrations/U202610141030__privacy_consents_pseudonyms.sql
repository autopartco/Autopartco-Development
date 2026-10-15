-- =============================================================================
-- U202610141030__privacy_consents_pseudonyms.sql  (rollback de V202610141030)
-- DESTRUCTIVO: requiere aprobación. Pierde la evidencia de consentimientos
-- (obligación legal de conservarla) y los seudónimos: tras recrearlos, los
-- datos de tracking/ml quedan desvinculados de sus personas.
-- Respaldo: pg_dump --data-only -t public.person_consents -t privacy.subject_pseudonyms "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS privacy.subject_pseudonyms;
DROP VIEW IF EXISTS public.person_consents_current;
DROP TABLE IF EXISTS public.person_consents;
DROP TABLE IF EXISTS public.consent_purposes;
