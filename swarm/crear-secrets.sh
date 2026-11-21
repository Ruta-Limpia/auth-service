#!/usr/bin/env bash
# Crea los secretos del stack si no existen. Se generan al azar, salvo que vengan
# en variables de entorno con el mismo nombre (DB_PASSWORD, JWT_SECRET, INTERNAL_API_KEY).
set -euo pipefail

crear() {
    local nombre="$1" valor="${2:-}"
    if docker secret inspect "$nombre" > /dev/null 2>&1; then
        echo "secreto $nombre: ya existe"
        return
    fi
    [ -n "$valor" ] || valor="$(openssl rand -hex 32)"
    printf '%s' "$valor" | docker secret create "$nombre" - > /dev/null
    echo "secreto $nombre: creado"
}

crear db_password "${DB_PASSWORD:-}"
crear jwt_secret "${JWT_SECRET:-}"
crear internal_api_key "${INTERNAL_API_KEY:-}"
