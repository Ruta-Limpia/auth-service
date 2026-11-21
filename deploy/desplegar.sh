#!/usr/bin/env bash
# Levanta auth-service y MySQL con Docker Compose y espera a que el servicio responda.
# Lo usa el pipeline para probar la imagen antes de publicarla. Tambien sirve en local.
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d --build --remove-orphans

echo "Esperando a auth-service..."
for intento in $(seq 1 60); do
    if curl -fsS http://localhost:8081/actuator/health > /dev/null 2>&1; then
        echo "auth-service responde (intento $intento)"
        docker compose ps
        docker image prune -f > /dev/null
        exit 0
    fi
    sleep 3
done

echo "auth-service no respondio a tiempo" >&2
docker compose ps >&2
docker compose logs --tail 100 auth-service >&2
exit 1
