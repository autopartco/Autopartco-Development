#!/usr/bin/env bash
# Aplica la réplica del esquema de Autopartco en una base PostgreSQL (Supabase).
# Orden: init.sql (línea base) -> V*.sql en orden de versión -> R__*.sql (semillas).
# No aplica U* (rollbacks) ni pending-approval/ (requieren aprobación).
#
# Uso (las credenciales van en variables de entorno, no en el archivo).
# Opción A: una URL completa (las contraseñas con # deben ir codificadas como %23):
#   export DATABASE_URL='postgresql://...'
# Opción B: variables libpq estándar:
#   export PGHOST=... PGPORT=5432 PGUSER=... PGPASSWORD=... PGDATABASE=postgres
#   bash database/scripts/aplicar_replica.sh

set -euo pipefail

if [[ -z "${DATABASE_URL:-}" && -z "${PGHOST:-}" ]]; then
  echo "Define DATABASE_URL o PGHOST/PGUSER/PGPASSWORD" >&2
  exit 1
fi

DB_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DB_DIR"

archivos=(init.sql)
while IFS= read -r f; do archivos+=("$f"); done < <(ls migrations/V*.sql | sort)
while IFS= read -r f; do archivos+=("$f"); done < <(ls migrations/R__*.sql | sort)

# DESDE=<archivo> reanuda la ejecución desde ese archivo (nombre base).
desde="${DESDE:-}"
activo=$([[ -z "$desde" ]] && echo 1 || echo 0)

for f in "${archivos[@]}"; do
  if [[ "$activo" == 0 ]]; then
    [[ "$(basename "$f")" == "$desde" ]] && activo=1 || continue
  fi
  echo ">> $f"
  # -X ignora psqlrc; ON_ERROR_STOP detiene al primer error.
  # Con .conf (executeInTransaction=false, p. ej. CREATE INDEX CONCURRENTLY):
  # sin transacción. Sin .conf: una sola transacción (-1), como indica cada cabecera.
  if [[ -f "$f.conf" ]]; then
    tx=""
  else
    tx="-1"
  fi
  psql -X $tx -v ON_ERROR_STOP=1 ${DATABASE_URL:+"$DATABASE_URL"} -f "$f"
done

echo "Réplica aplicada: ${#archivos[@]} archivos."
