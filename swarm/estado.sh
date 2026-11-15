#!/usr/bin/env bash
# Muestra el estado del cluster y del stack.
set -euo pipefail

echo "== Nodos"
docker node ls
echo
echo "== Servicios del stack"
docker stack services auth
echo
echo "== Replicas de auth-service"
docker service ps auth_auth-service --filter desired-state=running \
    --format 'table {{.Name}}\t{{.Node}}\t{{.CurrentState}}'
