# Despliegue en AWS (Learner Lab)

El pipeline (`.github/workflows/ci-cd.yml`) publica la imagen en Amazon ECR y despliega auth-service en un cluster Docker Swarm sobre EC2 cada vez que hay un push a `main`. Para conectarse usa las credenciales temporales de AWS Academy. No hay llaves SSH ni IPs guardadas en GitHub.

Cómo está armado el cluster y por qué: [swarm.md](swarm.md).

## 1. Crear la EC2 (una sola vez)

1. Entra al Learner Lab, presiona **Start Lab** y espera el punto verde. Luego abre la consola de AWS.
2. Ve a **EC2 > Launch instance** y completa:
   - **Name:** `auth-service`. El pipeline busca la instancia por este nombre.
   - **AMI:** Ubuntu Server 24.04 LTS. Ya trae EC2 Instance Connect.
   - **Instance type:** `t3.small` o mayor.
   - **Key pair:** `vockey`
   - **Security group:** permite **SSH (22)** y **TCP 8081** desde `0.0.0.0/0`. GitHub Actions se conecta desde IPs que cambian. El acceso sigue protegido porque solo se entra con una llave temporal que autoriza AWS.
   - **Storage:** 20 GB
3. Asígnale una **Elastic IP**: entra a **EC2 > Elastic IPs > Allocate**, y después a **Actions > Associate** con la instancia. Así la URL no cambia cuando el laboratorio se apaga.
4. **Worker (opcional):** crea una segunda instancia igual, con **Name** `auth-service-worker` y el **mismo security group**. No necesita Elastic IP ni prepararla: el pipeline le instala Docker y la une al cluster.

## 2. Preparar la EC2 (una sola vez)

En el Learner Lab, entra a **AWS Details > Download PEM** para bajar `labsuser.pem`. Luego, desde la carpeta del repo:

```bash
chmod 600 ~/Downloads/labsuser.pem
scp -i ~/Downloads/labsuser.pem deploy/preparar-ec2.sh ubuntu@<ELASTIC_IP>:~
ssh -i ~/Downloads/labsuser.pem ubuntu@<ELASTIC_IP> 'bash preparar-ec2.sh'
```

El script instala Docker y Docker Compose, agrega 1 GB de swap y crea la carpeta `~/auth-service`. Docker Swarm viene incluido en Docker, no hay que instalar nada más.

## 3. Credenciales de AWS Academy en GitHub

En el repo, entra a **Settings > Secrets and variables > Actions > New repository secret** y crea estos tres secretos:

| Nombre | De dónde sale |
|---|---|
| `AWS_ACCESS_KEY_ID` | Learner Lab, en **AWS Details > AWS CLI: Show** |
| `AWS_SECRET_ACCESS_KEY` | El mismo lugar |
| `AWS_SESSION_TOKEN` | El mismo lugar |

Estas credenciales **caducan cada vez que el laboratorio se reinicia**. Cuando abras una sesión nueva del Learner Lab, actualiza los tres secretos antes de desplegar.

**Opcionales**, como variables en **Settings > Secrets and variables > Actions > Variables**:

| Nombre | Para qué |
|---|---|
| `EC2_INSTANCE_NAME` | Si tu instancia no se llama `auth-service` |
| `EC2_WORKER_NAME` | Si el worker no se llama `auth-service-worker` |
| `SEED_DATA` | `never` por defecto. Con `always` se cargan los usuarios de prueba (clave `123456`) |

Si quieres los usuarios de prueba, define `SEED_DATA=always` **antes del primer deploy**. Usan los ids 1, 2 y 3, así que si ya hay usuarios registrados con esos ids, no se cargan.

Las claves de MySQL, JWT y la clave interna se generan solas en la EC2 en el primer deploy (`~/auth-service/.env`) y con ellas se crean los secretos del Swarm. Si quieres que el JWT sea el mismo de los otros microservicios, agrega el secreto opcional `JWT_SECRET` **antes del primer deploy en Swarm** (usa solo letras y números). Un secreto de Swarm no se puede cambiar mientras el stack lo usa; para cambiarlo hay que bajar el stack (`docker stack rm auth`), borrar el secreto con `docker secret rm` y volver a desplegar.

## 4. Desplegar

Haz un push a `main`, o entra a **Actions > CI/CD auth-service > Run workflow**. Los cuatro jobs son:

1. **Build y tests:** compila con Java 25, corre los tests y guarda el `.jar` y los reportes como artefactos.
2. **Imagen Docker y prueba con MySQL:** construye la imagen, la levanta con MySQL y prueba el health, el login y que no corra como root.
3. **Publicar imagen en Amazon ECR:** solo en `main`.
   1. Crea el repositorio ECR y las reglas del security group si no existen (`deploy/aws-recursos.sh`).
   2. Sube la imagen con dos tags: el hash del commit y `latest`.
4. **Deploy en Docker Swarm (EC2):** solo en `main`.
   1. Busca el manager y el worker por su nombre.
   2. Entra con EC2 Instance Connect, usando una llave de un solo uso.
   3. Si la EC2 todavía tenía la versión con Docker Compose, la baja.
   4. Inicia el Swarm y suma el worker, si existe.
   5. Despliega el stack con la imagen de ECR y espera a que las réplicas estén sanas. Si la versión nueva falla, Swarm vuelve a la anterior y el job falla.
   6. Muestra el estado del cluster y comprueba que el servicio responda desde internet.

El primer deploy en Swarm crea una base nueva (el volumen de Docker Compose queda guardado, pero no se usa). Si quieres los usuarios de prueba, deja `SEED_DATA=always` para ese deploy.

Para revisar que quedó arriba:

- `http://<ELASTIC_IP>:8081/actuator/health` debe responder `{"status":"UP"}`.
- Swagger está en `http://<ELASTIC_IP>:8081/swagger-ui.html`.

## Ambientes

| Rama | Qué hace | Ambiente |
|---|---|---|
| `feature/*` (PR) | Build, tests e imagen Docker | Revisión |
| `develop` | Build, tests e imagen Docker | Pruebas (QA) |
| `main` | Lo mismo y además el deploy en la EC2 | Producción |

## Evidencia para el foro

1. La pestaña **Actions** con el run de `main` en verde y los cuatro jobs.
2. El job **Publicar imagen en Amazon ECR** con "Crear recursos de AWS" y la subida de la imagen.
3. El job **Deploy en Docker Swarm (EC2)** abierto, con "Estado del cluster" y "Verificar desde internet" en verde.
4. La consola de ECR con el repositorio `auth-service` y las imágenes con el hash del commit.
5. La consola de EC2 con las instancias `auth-service` y `auth-service-worker` corriendo.
6. En el manager: `bash ~/auth-service/swarm/estado.sh` y `bash ~/auth-service/swarm/escalar.sh 4`.
7. El navegador o Postman contra `http://<ELASTIC_IP>:8081`: el health, un registro y un login.

## Learner Lab: tener en cuenta

- **El laboratorio se apaga solo** después de unas horas, y con él las EC2. Al volver a hacer *Start Lab*, las instancias arrancan y Swarm vuelve a levantar las réplicas solo. La IP privada no cambia, así que el worker se reconecta al manager.
- **Si el laboratorio está apagado o las credenciales caducaron**, el job de deploy falla en "Configurar credenciales" o en "Buscar la EC2". En ese caso enciende el lab, actualiza los tres secretos y vuelve a correr el workflow con **Re-run jobs**.
