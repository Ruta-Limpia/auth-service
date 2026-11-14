#!/usr/bin/env bash
# Se ejecuta una sola vez en la EC2 (Ubuntu 24.04): instala Docker y deja lista la carpeta.
set -euo pipefail

sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2 curl
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

# 1 GB de swap para que MySQL y la JVM no se queden sin memoria en instancias chicas
if [ ! -f /swapfile ]; then
    sudo fallocate -l 1G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
fi

mkdir -p ~/auth-service
echo "Listo. Sal de la sesion SSH y vuelve a entrar para usar docker sin sudo."
