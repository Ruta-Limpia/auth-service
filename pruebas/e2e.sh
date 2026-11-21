#!/usr/bin/env bash
# Prueba de punta a punta contra el API Gateway:
#   cliente -> API Gateway (HTTPS) -> auth-service en Swarm -> MySQL
#                                           |
#                                           +-> SQS -> Lambda -> SNS (correo)
#
# Uso: bash pruebas/e2e.sh https://xxxx.execute-api.us-east-1.amazonaws.com
# Si hay credenciales de AWS, tambien revisa en CloudWatch que la Lambda haya enviado la alerta.
# Variable opcional: LOG_LAMBDA (por defecto /aws/lambda/rl-auth-eventos-prod-alertas)
set -euo pipefail

API="${1:?falta la URL del API Gateway}"
API="${API%/}"
LOG_LAMBDA="${LOG_LAMBDA:-/aws/lambda/rl-auth-eventos-prod-alertas}"
EMAIL="e2e-$(date +%s)-$RANDOM@rutalimpia.cl"
CLAVE="E2e.Clave-$RANDOM$RANDOM"
inicio_ms=$(( $(date +%s) * 1000 ))
errores=0

ok()    { echo "  OK    $1"; }
falla() { echo "  FALLA $1" >&2; errores=$((errores + 1)); }

# pide algo al API y deja el codigo en $codigo y el cuerpo en $cuerpo
pedir() {
    local metodo="$1" ruta="$2" datos="${3:-}" token="${4:-}"
    local args=(-s -o /tmp/e2e-cuerpo.$$ -w '%{http_code}' -X "$metodo" --max-time 20)
    [ -n "$datos" ] && args+=(-H 'Content-Type: application/json' -d "$datos")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
    codigo=$(curl "${args[@]}" "$API$ruta" || echo 000)
    cuerpo=$(cat /tmp/e2e-cuerpo.$$ 2> /dev/null || true)
}
trap 'rm -f /tmp/e2e-cuerpo.$$' EXIT

esperar() { # descripcion codigo-esperado
    if [ "$codigo" = "$2" ]; then ok "$1 ($codigo)"; else falla "$1: se esperaba $2 y llego $codigo ${cuerpo:0:200}"; fi
}

echo "== API Gateway: $API"
if [[ "$API" == https://* ]]; then ok "la entrada publica es HTTPS"; else falla "la URL no es HTTPS"; fi

pedir GET /actuator/health
esperar "health a traves del API Gateway" 200

pedir GET /api/v1/internal/usuarios/1
esperar "la ruta interna no esta publicada en el API Gateway" 404

echo "== Flujo de usuario ($EMAIL)"
pedir POST /api/v1/auth/registro "{\"nombre\":\"Prueba E2E\",\"email\":\"$EMAIL\",\"password\":\"$CLAVE\"}"
esperar "registro" 201
usuario_id=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' <<< "$cuerpo")

pedir POST /api/v1/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$CLAVE\"}"
esperar "login correcto" 200
token=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' <<< "$cuerpo")

pedir GET /api/v1/usuarios/me "" "$token"
esperar "perfil con token" 200
if grep -q "$EMAIL" <<< "$cuerpo"; then ok "el perfil es del usuario registrado"; else falla "el perfil no trae el email"; fi

pedir GET /api/v1/usuarios/me
esperar "perfil sin token" 401

pedir GET /api/v1/usuarios "" "$token"
esperar "un vecino no puede listar usuarios" 403

for intento in 1 2 3; do
    pedir POST /api/v1/auth/login "{\"email\":\"$EMAIL\",\"password\":\"clave-equivocada\"}"
    esperar "login fallido $intento" 401
done

echo "== Productor y consumidor (SQS -> Lambda -> SNS)"
if [ -z "$usuario_id" ]; then
    falla "no se obtuvo el id del usuario, no se puede revisar la Lambda"
elif ! command -v aws > /dev/null || ! aws sts get-caller-identity > /dev/null 2>&1; then
    echo "  --    sin credenciales de AWS: no se revisan los logs de la Lambda"
else
    # el CLI puede devolver varias paginas (un numero por pagina), por eso se suman
    buscar() {
        aws logs filter-log-events --log-group-name "$LOG_LAMBDA" --start-time "$inicio_ms" \
            --filter-pattern "\"$1\"" --query 'length(events)' --output text 2> /dev/null \
            | awk '{ for (i = 1; i <= NF; i++) if ($i ~ /^[0-9]+$/) total += $i } END { print total + 0 }'
    }
    registrado=0 alerta=0
    for _ in $(seq 1 24); do
        [ "$registrado" -gt 0 ] || registrado=$(buscar "usuario nuevo registrado usuarioId=$usuario_id ")
        [ "$alerta" -gt 0 ] || alerta=$(buscar "ALERTA enviada usuarioId=$usuario_id ")
        [ "$registrado" -gt 0 ] && [ "$alerta" -gt 0 ] && break
        sleep 5
    done
    if [ "$registrado" -gt 0 ]; then ok "la Lambda proceso el evento de registro"
    else falla "la Lambda no proceso el evento de registro"; fi
    if [ "$alerta" -gt 0 ]; then ok "la Lambda envio la alerta por los 3 fallidos"
    else falla "la Lambda no envio la alerta"; fi
fi

echo
if [ "$errores" -eq 0 ]; then
    echo "Prueba e2e correcta"
else
    echo "Prueba e2e con $errores error(es)" >&2
    exit 1
fi
