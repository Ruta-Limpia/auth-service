#!/usr/bin/env bash
# Une este nodo al Swarm como worker. Si ya es parte de un Swarm no hace nada.
# Uso: bash swarm/unir-worker.sh <token-worker> <ip-privada-del-manager>
set -euo pipefail

TOKEN="${1:?falta el token (lo muestra init-manager.sh)}"
MANAGER="${2:?falta la IP privada del manager}"

if [ "$(docker info --format '{{.Swarm.LocalNodeState}}')" = "active" ]; then
    echo "este nodo ya es parte del Swarm"
    exit 0
fi
docker swarm join --token "$TOKEN" "$MANAGER:2377"
