-- =============================================================================
-- U202610141320__analytics_phase2.sql  (rollback de V202610141320)
-- Borra agregados, vistas materializadas y funciones. Todo es regenerable
-- desde el OLTP, tracking e inventory_daily_snapshots mientras esas fuentes
-- conserven los datos (sin guard destructivo).
-- =============================================================================

DROP VIEW IF EXISTS analytics.v_supplier_lead_times;
DROP MATERIALIZED VIEW IF EXISTS analytics.mv_customer_rfm;
DROP MATERIALIZED VIEW IF EXISTS analytics.mv_customer_cohorts;
DROP MATERIALIZED VIEW IF EXISTS analytics.mv_inventory_rotation_monthly;
DROP FUNCTION IF EXISTS analytics.refresh_fact_returns_daily(DATE, DATE);
DROP FUNCTION IF EXISTS analytics.refresh_fact_search_funnel_daily(DATE, DATE);
DROP FUNCTION IF EXISTS analytics.refresh_fact_vehicle_demand_daily(DATE, DATE);
DROP TABLE IF EXISTS analytics.fact_returns_daily;
DROP TABLE IF EXISTS analytics.fact_search_funnel_daily;
DROP TABLE IF EXISTS analytics.fact_vehicle_demand_daily;
