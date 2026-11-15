# auth-service en Docker Swarm

En producción auth-service corre en un cluster de Docker Swarm sobre EC2, con varias réplicas detrás del balanceo que trae Swarm. El stack está en `docker-stack.yml` y los scripts para manejarlo en la carpeta `swarm/`.

## Cómo queda armado

```
                    internet :8081
                          |
          +---------------+----------------+
          |     routing mesh de Swarm      |   (cualquier nodo recibe y reparte)
          +-------+----------------+-------+
                  |                |
   EC2 auth-service (manager)    EC2 auth-service-worker (worker)
   +-----------------------+     +-----------------------+
   | auth-service réplica  |     | auth-service réplica  |
   | auth-service réplica  |     | auth-service réplica  |
   | mysql (volumen)       |     |                       |
   +-----------+-----------+     +-----------+-----------+
               |       red overlay "backend"  |
               +------------------------------+
```

- **Manager:** la EC2 `auth-service`. Administra el cluster y además corre réplicas. MySQL queda fijo en este nodo porque ahí está su volumen.
- **Worker:** la EC2 `auth-service-worker`. Solo corre réplicas de auth-service. Es opcional: sin worker, todo corre en el manager.
- La imagen se baja de **Amazon ECR**. El pipeline la sube con el hash del commit como tag.

## Scripts

| Script | Dónde se corre | Qué hace |
|---|---|---|
| `swarm/init-manager.sh [ip-privada]` | manager | Inicia el Swarm y marca el nodo como el de la base (`rl.db=true`). Si ya existe, no hace nada |
| `swarm/unir-worker.sh <token> <ip-manager>` | worker | Une el nodo al cluster. Si ya está unido, no hace nada |
| `swarm/crear-secrets.sh` | manager | Crea los secretos `db_password`, `jwt_secret` e `internal_api_key` si no existen |
| `swarm/desplegar-stack.sh` | manager | Crea los secretos, despliega el stack y espera a que todas las réplicas estén sanas. Si Swarm hace rollback, termina con error |
| `swarm/escalar.sh <n>` | manager | Cambia la cantidad de réplicas y muestra en qué nodo quedó cada una |
| `swarm/estado.sh` | manager | Muestra los nodos, los servicios y las réplicas |
| `deploy/aws-recursos.sh` | pipeline | Crea el repositorio ECR y abre los puertos del Swarm en el security group |

El pipeline corre todo esto solo en cada push a `main`. Los pasos manuales de abajo sirven para entender qué hace o para probar a mano.

## Paso a paso

### 1. Crear el worker (una sola vez)

En la consola de EC2, **Launch instance** igual que la primera, con dos cambios:

- **Name:** `auth-service-worker`
- **Security group:** elegir el **mismo** que usa `auth-service`. Los puertos del Swarm se abren solo entre máquinas de ese grupo.

No necesita Elastic IP. El pipeline lo encuentra por su nombre, le instala Docker si no lo tiene y lo une al cluster.

### 2. Puertos

`deploy/aws-recursos.sh` agrega estas reglas al security group, permitidas solo desde el mismo grupo:

| Puerto | Para qué |
|---|---|
| 2377/tcp | administración del cluster (los workers hablan con el manager) |
| 7946/tcp y udp | descubrimiento entre nodos |
| 4789/udp | tráfico de la red overlay (VXLAN) |

El 22 y el 8081 ya estaban abiertos para el deploy y para usar la API.

### 3. Desplegar a mano (si se quiere probar sin el pipeline)

En el manager, dentro de `~/auth-service`:

```bash
bash swarm/init-manager.sh
set -a; . ./.env; set +a
AUTH_IMAGE=<cuenta>.dkr.ecr.us-east-1.amazonaws.com/auth-service:latest bash swarm/desplegar-stack.sh
```

Para sumar el worker, en el manager se saca el token con `docker swarm join-token -q worker` y en el worker se corre `bash swarm/unir-worker.sh <token> <ip-privada-del-manager>`.

### 4. Escalar

```bash
bash swarm/escalar.sh 4     # sube a 4 réplicas, repartidas entre los nodos
bash swarm/escalar.sh 2     # vuelve a 2
bash swarm/estado.sh
```

Mientras se escala, el servicio sigue respondiendo: las réplicas nuevas entran al balanceo recién cuando su healthcheck pasa.

Si se suma un worker con el stack ya corriendo, Swarm no mueve las réplicas que ya están arriba. Para repartirlas de nuevo: `docker service update --force auth_auth-service`.

### 5. Probar en local

Con Docker Desktop basta un solo nodo:

```bash
docker build -t auth-service:local .
docker swarm init
bash swarm/init-manager.sh
bash swarm/desplegar-stack.sh
```

Para salir del modo Swarm: `docker stack rm auth` y `docker swarm leave --force`.

## Por qué está configurado así

**Varias réplicas (2 por defecto).** Si una réplica se cae o se está actualizando, la otra sigue atendiendo. auth-service no guarda sesión en memoria (el login entrega un JWT), así que cualquier réplica puede responder cualquier petición. Lo probamos registrando un usuario en una réplica y haciendo login en la otra.

**Routing mesh en el puerto 8081.** Swarm publica el puerto en todos los nodos y reparte las peticiones entre las réplicas sanas, estén en el nodo que estén. No hace falta un balanceador aparte.

**Actualización sin caída (`order: start-first`).** En cada deploy Swarm levanta la réplica nueva, espera que el healthcheck pase y recién ahí baja la antigua, de a una (`parallelism: 1`). En una prueba con 400 peticiones durante una actualización no falló ninguna.

**Rollback automático (`failure_action: rollback`, `monitor: 60s`).** Si la versión nueva no arranca o se cae en su primer minuto, Swarm vuelve sola a la anterior. `desplegar-stack.sh` lo detecta y hace fallar el pipeline. Lo probamos desplegando a propósito una versión que no podía conectarse a la base.

**Healthcheck en la imagen.** El `HEALTHCHECK` del Dockerfile consulta `/actuator/health`. Swarm lo usa para saber cuándo una réplica está lista y para reemplazarla si deja de responder (`restart_policy: any`).

**Secretos de Swarm en vez de variables.** Las claves de MySQL, JWT y la API interna quedan cifradas en el cluster y cada contenedor las ve como archivos en `/run/secrets`. No aparecen en `docker inspect` ni en el stack. Spring las lee como propiedades con `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`, sin cambiar el código. MySQL usa `MYSQL_ROOT_PASSWORD_FILE`.

**Red overlay `backend`.** Conecta las réplicas con MySQL aunque estén en EC2 distintas. MySQL no publica puertos, así que solo se puede llegar a él desde dentro del cluster.

**MySQL con una réplica, fija en el manager.** Una base no se puede replicar solo subiendo `replicas`. Se queda en el nodo con la etiqueta `rl.db=true`, que es donde está su volumen, para no perder los datos si Swarm la reinicia. Usa `endpoint_mode: dnsrr`: como es un solo contenedor, el nombre `mysql` apunta directo a él y no a una IP virtual.

**Límites de memoria y CPU.** Cada réplica tiene máximo 768 MB y 1 CPU, y reserva 384 MB. Así una réplica con problemas no deja sin memoria al resto, y Swarm no pone más réplicas en un nodo de las que caben. En la práctica cada réplica usa unos 255 MB. La JVM usa el 60% del límite del contenedor (`-XX:MaxRAMPercentage=60`).

**La imagen en ECR con el hash del commit.** Cada versión queda identificada y se puede volver a cualquiera. ECR está en la misma cuenta de AWS, escanea la imagen al subirla y borra solo las antiguas (se guardan las últimas 10). El manager pasa el acceso a ECR a los workers con `--with-registry-auth`.

## Lo que falta para un ambiente real

- **Un solo manager.** Si el manager se cae, las réplicas siguen corriendo, pero no se puede desplegar ni reprogramar nada. En producción se usan 3 managers, para que el cluster siga funcionando si se pierde uno (Raft necesita mayoría).
- **MySQL es un punto único de falla.** Si su nodo se cae, auth-service queda sin base. Lo correcto sería una base administrada como Amazon RDS con Multi-AZ.
- **El acceso a ECR dura 12 horas.** Si después de ese tiempo Swarm tiene que mover una réplica a un nodo que no tiene la imagen, no podrá bajarla hasta el siguiente deploy. Con el rol `LabRole` asignado a las EC2 y el credential helper de ECR se resolvería.
