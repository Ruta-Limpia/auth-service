# Despliegue en AWS (Learner Lab)

El pipeline (`.github/workflows/ci-cd.yml`) despliega auth-service en una EC2 cada vez que hay un push a `main`. Esta guía deja la EC2 y GitHub listos. Se hace una sola vez.

## 1. Crear la EC2

1. Entra al Learner Lab, presiona **Start Lab** y espera el punto verde. Luego abre la consola de AWS.
2. Ve a **EC2 > Launch instance** y completa:
   - **Name:** `auth-service`
   - **AMI:** Ubuntu Server 24.04 LTS
   - **Instance type:** `t3.small`, o `t3.medium` si está disponible. Con `t3.micro`, MySQL y Java quedan muy justos.
   - **Key pair:** `vockey`
   - **Security group:** permite **SSH (22)** y **TCP 8081** desde `0.0.0.0/0`. El 22 tiene que estar abierto a todo internet porque GitHub Actions se conecta desde IPs que cambian. El acceso sigue protegido por la llave.
   - **Storage:** 20 GB
3. Asígnale una **Elastic IP**: en **EC2 > Elastic IPs > Allocate Elastic IP address**, y luego **Actions > Associate** con la instancia. Así la IP no cambia cada vez que el laboratorio se apaga.

## 2. Preparar la EC2

En el Learner Lab, entra a **AWS Details > Download PEM** para bajar `labsuser.pem`. Luego, desde la carpeta del repo:

```bash
chmod 600 ~/Downloads/labsuser.pem
scp -i ~/Downloads/labsuser.pem deploy/preparar-ec2.sh ubuntu@<ELASTIC_IP>:~
ssh -i ~/Downloads/labsuser.pem ubuntu@<ELASTIC_IP> 'bash preparar-ec2.sh'
```

El script instala Docker y Docker Compose, agrega 1 GB de swap y crea la carpeta `~/auth-service`.

## 3. Configurar GitHub

En el repo, entra a **Settings > Environments > New environment** y crea el ambiente `produccion`.

**Secrets** (sección *Environment secrets*):

| Nombre | Valor |
|---|---|
| `EC2_SSH_KEY` | El contenido completo de `labsuser.pem`, desde `-----BEGIN` hasta `-----END` |
| `DB_PASSWORD` | La clave de MySQL |
| `JWT_SECRET` | Al menos 32 caracteres |
| `INTERNAL_API_KEY` | La clave de las rutas internas |

Para generar claves seguras usa `openssl rand -hex 32`. Usa solo letras y números, sin espacios ni `#`, porque estos valores se escriben en un archivo `.env`.

**Variables** (sección *Environment variables*):

| Nombre | Valor |
|---|---|
| `EC2_HOST` | La Elastic IP |
| `EC2_USER` | `ubuntu` (opcional; es el valor por defecto) |
| `SEED_DATA` | `never` (opcional; es el valor por defecto) |

Con `SEED_DATA=never` no se cargan los usuarios de prueba, que tienen la clave `123456`. Así nadie entra como admin a un servidor público. Para una demo puedes ponerlo en `always` por un rato, pero los datos semilla quedan cargados en la base. Para volver a quitarlos tienes que eliminar esos usuarios.

**Opcional:** en el ambiente `produccion`, activa **Required reviewers**. Así el deploy espera tu aprobación antes de salir a producción.

## 4. Desplegar

Haz un push a `main`, o entra a **Actions > CI/CD auth-service > Run workflow**. El pipeline tiene tres jobs:

1. **Build y tests:** compila con Java 25, corre los tests y guarda el `.jar` y los reportes como artefactos.
2. **Imagen Docker y prueba con MySQL:** construye la imagen, la levanta junto a MySQL y prueba el health y el login. La imagen no se publica en ningún registro.
3. **Deploy en AWS EC2:** solo corre en `main`. Copia el jar a la EC2, levanta los contenedores y comprueba que el servicio responda desde internet.

Para revisar que quedó arriba:

- `http://<ELASTIC_IP>:8081/actuator/health` debe responder `{"status":"UP"}`.
- Swagger está en `http://<ELASTIC_IP>:8081/swagger-ui.html`.

## Ambientes

| Rama | Qué hace | Ambiente |
|---|---|---|
| `feature/*` (PR) | Build, tests e imagen Docker | Revisión |
| `develop` | Build, tests e imagen Docker | Pruebas (QA) |
| `main` | Lo mismo, más el deploy en la EC2 | Producción |

La configuración de cada ambiente va en variables de entorno y secretos de GitHub. En el repo no hay credenciales de producción.

## Evidencia para el foro

Toma estas capturas:

1. La pestaña **Actions** con el run en verde y los tres jobs.
2. El detalle del job **Deploy en AWS EC2**, con el paso "Verificar desde internet" en verde.
3. La sección **Artifacts** del run, con el jar y los reportes de tests.
4. El ambiente **produccion**, en la portada del repo o en Settings, con la URL del último deploy.
5. La consola de EC2 con la instancia corriendo y su Elastic IP.
6. El navegador o Postman contra `http://<ELASTIC_IP>:8081`: el health, un registro y un login.

## Learner Lab: tener en cuenta

- **El laboratorio se apaga solo** después de unas horas, y con él la EC2. Al volver a hacer *Start Lab*, la instancia arranca y Docker vuelve a levantar los contenedores solo, gracias a `restart: unless-stopped`.
- **Si el laboratorio está apagado**, el job de deploy falla en el paso de SSH. Es lo esperado: enciende el lab y vuelve a correr el workflow.
- **La llave `labsuser.pem` no cambia** entre sesiones, así que el secreto `EC2_SSH_KEY` se configura una sola vez.
