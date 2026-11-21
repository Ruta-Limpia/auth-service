#!/usr/bin/env bash
# Cambia la cantidad de replicas de auth-service y muestra donde quedaron.
# Uso: bash swarm/escalar.sh <replicas>
set -euo pipefail

N="${1:?indica la cantidad de replicas, por ejemplo: bash swarm/escalar.sh 4}"

docker service scale "auth_auth-service=$N"
docker service ps auth_auth-service --filter desired-state=running \
    --format 'table {{.Name}}\t{{.Node}}\t{{.CurrentState}}'
