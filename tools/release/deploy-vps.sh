#!/usr/bin/env bash
# Despliega el backend de main en el VPS (Hostinger, api.autopartco.co).
#
# Uso:   bash tools/release/deploy-vps.sh [REF]      (REF por defecto: origin/main)
# Variables: VPS_HOST (def. 179.66.141.169), VPS_USER (def. root), SSH_KEY (ruta opcional a la llave).
# Servidor: /opt/autopartco/{run.sh,backend.env}; el contenedor se llama autopartco-backend.
# Si el health check falla, vuelve a la imagen anterior.
set -euo pipefail

REF="${1:-origin/main}"
VPS_HOST="${VPS_HOST:-179.66.141.169}"
VPS_USER="${VPS_USER:-root}"
SSH_OPTS=(-o BatchMode=yes -o StrictHostKeyChecking=accept-new)
[ -n "${SSH_KEY:-}" ] && SSH_OPTS+=(-i "$SSH_KEY")
remote() { ssh "${SSH_OPTS[@]}" "$VPS_USER@$VPS_HOST" "$@"; }

git rev-parse --verify -q "$REF" >/dev/null || { echo "REF no existe: $REF" >&2; exit 1; }
SHA="$(git rev-parse --short "$REF")"
echo "Desplegando $REF ($SHA) en $VPS_HOST"

echo "1/4 Enviando backend/"
git archive "$REF" backend | remote 'rm -rf /opt/autopartco/src && mkdir -p /opt/autopartco/src && tar -x -C /opt/autopartco/src'

echo "2/4 Construyendo imagen"
remote "set -e
  docker tag autopartco-backend autopartco-backend:prev 2>/dev/null || true
  cd /opt/autopartco/src/backend && docker build -q -t autopartco-backend --label commit=$SHA . >/dev/null"

echo "3/4 Reiniciando contenedor"
remote '/opt/autopartco/run.sh >/dev/null'

echo "4/4 Verificando salud"
for _ in $(seq 1 30); do
  if remote 'curl -fsS -m 5 http://127.0.0.1:8081/actuator/health | grep -q "\"UP\""'; then
    echo "OK: $SHA en producción"
    remote 'docker image prune -f >/dev/null'
    exit 0
  fi
  sleep 5
done

echo "Health check falló; volviendo a la imagen anterior" >&2
remote 'docker logs --tail 30 autopartco-backend 2>&1 | grep -viE "password|secret|key" | cut -c1-200' >&2 || true
remote 'docker image inspect autopartco-backend:prev >/dev/null 2>&1 && docker tag autopartco-backend:prev autopartco-backend && /opt/autopartco/run.sh >/dev/null' || true
exit 1
