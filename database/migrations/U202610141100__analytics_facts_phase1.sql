-- =============================================================================
-- U202610141100__analytics_facts_phase1.sql  (rollback de V202610141100)
-- Borra los agregados y sus funciones. Los datos se regeneran con las
-- funciones de refresco a partir del OLTP y de tracking, salvo
-- fact_unmet_demand_daily si las particiones de tracking ya se purgaron.
-- Revierta antes U202610141300 (ml lee de analytics) y U202610141320.
-- =============================================================================

DROP FUNCTION IF EXISTS analytics.refresh_fact_unmet_demand_daily(DATE, DATE);
DROP FUNCTION IF EXISTS analytics.refresh_fact_order_fulfillment(TIMESTAMPTZ);
DROP FUNCTION IF EXISTS analytics.refresh_fact_sales_daily(DATE, DATE);
DROP TABLE IF EXISTS analytics.fact_unmet_demand_daily;
DROP TABLE IF EXISTS analytics.fact_order_fulfillment;
DROP TABLE IF EXISTS analytics.fact_sales_daily;
