# auth-service en Docker Swarm

Para esta entrega pasamos auth-service de Docker Compose a Docker Swarm. Así podemos tener varias copias (réplicas) del servicio repartidas en dos EC2, y Swarm se encarga de balancear entre ellas. El stack está en `docker-stack.yml`, y los scripts para manejarlo, en la carpeta `swarm/`.

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
| `deploy/aws-recursos.sh` | pipeline | Crea el repositorio ECR, abre los puertos del Swarm en el security group y le pone el rol `LabInstanceProfile` a las EC2 |

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

## Por qué lo configuramos así

**2 réplicas.** Si una se cae o se está actualizando, la otra sigue atendiendo. Como el login entrega un JWT y auth-service no guarda sesiones en memoria, cualquier réplica puede atender cualquier petición. Para comprobarlo registramos un usuario en una réplica e hicimos login en la otra, y funcionó.

**Routing mesh en el 8081.** Swarm abre el puerto en todos los nodos y reparte las peticiones entre las réplicas que estén sanas, en el nodo que sea. Por eso no necesitamos un balanceador aparte.

**Actualizar sin cortar el servicio (`order: start-first`).** En cada deploy, Swarm primero levanta la réplica nueva, espera que pase el healthcheck y después baja la antigua, de a una. Lo probamos mandando 400 peticiones mientras se actualizaba, y no falló ninguna.

**Rollback automático.** Si la versión nueva no arranca o se cae en el primer minuto, Swarm vuelve sola a la anterior (`failure_action: rollback`). El script `desplegar-stack.sh` se da cuenta y hace fallar el pipeline. Para probarlo subimos a propósito una versión que no podía conectarse a la base, y Swarm la revirtió.

**Healthcheck.** El Dockerfile tiene un `HEALTHCHECK` que consulta `/actuator/health`. Con eso Swarm sabe cuándo una réplica está lista, y si deja de responder la reemplaza.

**Secretos de Swarm.** Las claves de MySQL, JWT y la clave interna no van como variables de entorno, sino como secretos. Swarm las guarda cifradas y cada contenedor las ve como archivos en `/run/secrets`. No hubo que cambiar el código: Spring las lee como propiedades con `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`. MySQL lee su clave con `MYSQL_ROOT_PASSWORD_FILE`.

**Red overlay.** La red `backend` conecta las réplicas con MySQL aunque estén en EC2 distintas. MySQL no publica ningún puerto, así que solo se puede llegar a la base desde dentro del cluster.

**MySQL con una sola réplica, fija en el manager.** Una base de datos no se replica solo subiendo `replicas`, porque cada copia tendría sus propios datos. Por eso la dejamos fija en el nodo con la etiqueta `rl.db=true`, que es donde está su volumen. Además le pusimos `endpoint_mode: dnsrr`. Al principio las réplicas no se podían conectar a MySQL, y con eso el nombre `mysql` apunta directo al contenedor y se arregló.

**Límites de memoria y CPU.** Cada réplica puede usar como máximo 768 MB y 1 CPU, y reserva 384 MB. Así, si una réplica se descontrola, no deja sin memoria a las demás, y Swarm no mete en un nodo más réplicas de las que caben. En la práctica cada una usa unos 255 MB.

**Imagen en ECR con el hash del commit.** Cada versión queda con su propio tag, así sabemos qué commit está corriendo y podemos volver a uno anterior. Elegimos ECR porque está en la misma cuenta de AWS, revisa vulnerabilidades al subir la imagen y borra solo las imágenes viejas (deja las últimas 10).

## Lo que mejoraríamos

- **Un solo manager.** Si se cae el manager, las réplicas siguen funcionando, pero no se puede desplegar ni mover nada. Lo recomendado es tener 3 managers, para que el cluster siga funcionando aunque se pierda uno.
- **MySQL es un punto único de falla.** Si se cae su nodo, auth-service se queda sin base. Lo mejor sería usar Amazon RDS con Multi-AZ.
- **El login a ECR dura 12 horas.** Si después de ese tiempo Swarm tiene que mover una réplica a un nodo que no tiene la imagen, no la va a poder bajar hasta el siguiente deploy.
