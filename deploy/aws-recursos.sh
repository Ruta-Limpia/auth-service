#!/usr/bin/env bash
# Crea en AWS lo que necesita el despliegue, si es que falta:
#  - repositorio ECR para la imagen, con escaneo de vulnerabilidades al subir
#    y una regla que borra las imagenes antiguas (se guardan las ultimas 10)
#  - reglas del security group de la EC2 para que los nodos del Swarm se comuniquen
#  - rol LabInstanceProfile en las EC2, para que auth-service pueda escribir en SQS
# Se puede correr las veces que sea: lo que ya existe no se modifica.
# Uso: bash deploy/aws-recursos.sh [nombre-de-la-ec2] [nombre-del-worker]
set -euo pipefail

REPO="${ECR_REPO:-auth-service}"
INSTANCIA="${1:-${INSTANCIA:-auth-service}}"
WORKER="${2:-${WORKER:-auth-service-worker}}"

if aws ecr describe-repositories --repository-names "$REPO" > /dev/null 2>&1; then
    echo "repositorio ECR $REPO: ya existe"
else
    aws ecr create-repository --repository-name "$REPO" \
        --image-scanning-configuration scanOnPush=true > /dev/null
    echo "repositorio ECR $REPO: creado"
fi

aws ecr put-lifecycle-policy --repository-name "$REPO" --lifecycle-policy-text '{
  "rules": [{
    "rulePriority": 1,
    "description": "guardar solo las ultimas 10 imagenes",
    "selection": {"tagStatus": "any", "countType": "imageCountMoreThan", "countNumber": 10},
    "action": {"type": "expire"}
  }]
}' > /dev/null \
    && echo "repositorio ECR $REPO: limpieza de imagenes antiguas configurada" \
    || echo "aviso: no se pudo configurar la limpieza de imagenes en ECR (se sigue igual)" >&2

SG=$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=$INSTANCIA" "Name=instance-state-name,Values=running" \
    --query 'Reservations[].Instances[] | [0].SecurityGroups[0].GroupId' --output text)
if [ -z "$SG" ] || [ "$SG" = "None" ]; then
    echo "No hay una EC2 corriendo con el nombre '$INSTANCIA'" >&2
    exit 1
fi

# puertos del Swarm, abiertos solo entre las maquinas del mismo security group:
# 2377 administracion del cluster, 7946 comunicacion entre nodos, 4789 red overlay
error=$(mktemp)
trap 'rm -f "$error"' EXIT
abrir() {
    local protocolo="$1" puerto="$2"
    if aws ec2 authorize-security-group-ingress --group-id "$SG" \
        --ip-permissions "IpProtocol=$protocolo,FromPort=$puerto,ToPort=$puerto,UserIdGroupPairs=[{GroupId=$SG,Description=docker-swarm}]" \
        > /dev/null 2> "$error"; then
        echo "security group $SG: $puerto/$protocolo abierto entre nodos"
    elif grep -q 'InvalidPermission.Duplicate' "$error"; then
        echo "security group $SG: $puerto/$protocolo ya estaba abierto"
    else
        cat "$error" >&2
        exit 1
    fi
}
abrir tcp 2377
abrir tcp 7946
abrir udp 7946
abrir udp 4789

# los contenedores no tienen llaves de AWS: piden credenciales temporales al servicio
# de metadatos de la EC2. Para eso la EC2 necesita un rol, y como el contenedor esta
# un salto de red mas lejos, el limite de saltos tiene que ser 2
asegurar_rol() {
    local nombre="$1" id perfil
    read -r id perfil <<< "$(aws ec2 describe-instances \
        --filters "Name=tag:Name,Values=$nombre" "Name=instance-state-name,Values=running" \
        --query 'Reservations[].Instances[] | [0].[InstanceId, IamInstanceProfile.Arn]' --output text)"
    if [ -z "$id" ] || [ "$id" = "None" ]; then
        echo "EC2 $nombre: no esta corriendo, se omite"
        return
    fi
    if [ "$perfil" = "None" ] || [ -z "$perfil" ]; then
        aws ec2 associate-iam-instance-profile --instance-id "$id" \
            --iam-instance-profile Name=LabInstanceProfile > /dev/null
        echo "EC2 $nombre: rol LabInstanceProfile asignado"
    else
        echo "EC2 $nombre: ya tiene rol"
    fi
    aws ec2 modify-instance-metadata-options --instance-id "$id" \
        --http-endpoint enabled --http-put-response-hop-limit 2 > /dev/null
    echo "EC2 $nombre: credenciales disponibles para los contenedores"
}
asegurar_rol "$INSTANCIA"
asegurar_rol "$WORKER"
