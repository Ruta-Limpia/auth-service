#!/usr/bin/env bash
# Despliega (o actualiza) el stack "auth" en el Swarm y espera a que responda.
# Se ejecuta en un nodo manager. La imagen se elige con AUTH_IMAGE.
set -euo pipefail

cd "$(dirname "$0")/.."
STACK=auth
SERVICIO="${STACK}_auth-service"

bash swarm/crear-secrets.sh

# se guarda cuando partio la ultima actualizacion, para no confundir un rollback
# antiguo con uno provocado por este despliegue
inicio_previo=$(docker service inspect "$SERVICIO" \
    --format '{{if .UpdateStatus}}{{.UpdateStatus.StartedAt}}{{end}}' 2>/dev/null || true)

docker stack deploy --with-registry-auth --prune -c docker-stack.yml "$STACK"

echo "Esperando a que las replicas esten listas..."
for intento in $(seq 1 60); do
    estado=$(docker service inspect "$SERVICIO" --format '{{if .UpdateStatus}}{{.UpdateStatus.State}}{{end}}')
    inicio=$(docker service inspect "$SERVICIO" --format '{{if .UpdateStatus}}{{.UpdateStatus.StartedAt}}{{end}}')
    [ "$inicio" = "$inicio_previo" ] && [[ "$estado" == rollback* ]] && estado=anterior
    replicas=$(docker service ls --filter "name=$SERVICIO" --format '{{.Replicas}}')
    if [[ "$estado" == rollback* ]]; then
        echo "La actualizacion fallo y Swarm volvio a la version anterior" >&2
        docker service ps "$SERVICIO" --no-trunc 2>&1 | head -n 8 >&2 || true
        exit 1
    fi
    actuales="${replicas%%/*}"
    deseadas="${replicas##*/}"
    deseadas="${deseadas%% *}"
    if [ "$estado" != "updating" ] && [ -n "$actuales" ] && [ "$actuales" = "$deseadas" ] \
        && curl -fsS http://localhost:8081/actuator/health > /dev/null 2>&1; then
        echo "Stack listo (intento $intento): $replicas replicas"
        docker stack services "$STACK"
        exit 0
    fi
    sleep 5
done

echo "El stack no quedo listo a tiempo" >&2
docker stack ps "$STACK" --no-trunc 2>&1 | head -n 15 >&2 || true
exit 1
