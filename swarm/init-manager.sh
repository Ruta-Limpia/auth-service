#!/usr/bin/env bash
# Inicia el cluster Swarm en este nodo (queda como manager) y lo marca como el nodo de la base.
# Uso: bash swarm/init-manager.sh [ip-privada]
set -euo pipefail

if [ "$(docker info --format '{{.Swarm.LocalNodeState}}')" != "active" ]; then
    IP="${1:-$(hostname -I | awk '{print $1}')}"
    # la salida de "swarm init" trae el token para unir workers; no se muestra
    docker swarm init --advertise-addr "$IP" > /dev/null
    echo "Swarm iniciado, este nodo es el manager ($IP)"
else
    echo "Este nodo ya es parte de un Swarm"
fi

docker node update --label-add rl.db=true "$(docker info --format '{{.Swarm.NodeID}}')" > /dev/null

# el token no se imprime para que no quede en los logs del pipeline
echo
echo "Para sumar un worker: obten el token con 'docker swarm join-token -q worker'"
echo "y en el otro nodo ejecuta: bash swarm/unir-worker.sh <token> $(docker info --format '{{.Swarm.NodeAddr}}')"
