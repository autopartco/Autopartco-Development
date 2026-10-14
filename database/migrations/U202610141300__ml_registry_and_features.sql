-- =============================================================================
-- U202610141300__ml_registry_and_features.sql  (rollback de V202610141300)
-- DESTRUCTIVO: requiere aprobación (registro de modelos, features y
-- predicciones con sus resultados: se pierde la trazabilidad). Revierta antes
-- U202610141310 (payment_risk_assessments.model_version_id).
-- Respaldo: pg_dump --data-only -n ml "$DATABASE_URL" > ml.sql
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

ALTER TABLE public.inventory_reorder_policies DROP CONSTRAINT IF EXISTS fk_inventory_reorder_policies__model_version_id;
DROP VIEW IF EXISTS ml.v_current_demand_forecast;
DROP TABLE IF EXISTS ml.prediction_outcomes;
DROP TABLE IF EXISTS ml.predictions;
DROP TABLE IF EXISTS ml.branch_features;
DROP TABLE IF EXISTS ml.customer_features;
DROP TABLE IF EXISTS ml.product_branch_features;
DROP TABLE IF EXISTS ml.feature_definitions;
DROP TABLE IF EXISTS ml.batch_runs;
DROP TABLE IF EXISTS ml.model_versions;
DROP TABLE IF EXISTS ml.datasets;
DROP TABLE IF EXISTS ml.models;
