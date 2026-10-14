-- =============================================================================
-- U202610130900__extensions_and_helpers.sql  (rollback de V202610130900)
--
-- Revierte solo lo que la V añadió: immutable_unaccent, pg_trgm y unaccent.
-- No toca pgcrypto ni set_updated_at(): pertenecen a la línea base (init.sql).
-- DROP EXTENSION sin CASCADE falla si quedan objetos dependientes (índices
-- trigram, products.search_vector): aplique antes los U de 0942 y 0940.
--
-- psql -v ON_ERROR_STOP=1 --single-transaction -f <archivo>
-- =============================================================================

SET LOCAL lock_timeout = '5s';

DROP FUNCTION IF EXISTS public.immutable_unaccent(TEXT);
DROP EXTENSION IF EXISTS unaccent;
DROP EXTENSION IF EXISTS pg_trgm;
