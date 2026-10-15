-- =============================================================================
-- U202610140930__calendar_dimension.sql  (rollback de V202610140930)
-- Borra el calendario. Los datos son regenerables (funciones + R__), por eso
-- no se exige el guard destructivo. Revierta antes los objetos analíticos que
-- usan dim_date (U202610141100, U202610141320).
-- =============================================================================

DROP FUNCTION IF EXISTS analytics.populate_dim_date(DATE, DATE);
DROP FUNCTION IF EXISTS analytics.co_national_holidays(INT);
DROP FUNCTION IF EXISTS analytics.next_monday(DATE);
DROP FUNCTION IF EXISTS analytics.easter_sunday(INT);
DROP TABLE IF EXISTS analytics.dim_date;
DROP TABLE IF EXISTS analytics.holidays;
