#!/usr/bin/env bash
# Levanta auth-service y MySQL con Docker Compose y espera a que el servicio responda.
# Lo ejecuta el pipeline en la EC2. En local sirve despues de ./mvnw clean package.
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
