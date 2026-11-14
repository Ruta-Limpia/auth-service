# Despliegue en AWS (Learner Lab)

El pipeline (`.github/workflows/ci-cd.yml`) despliega auth-service en una EC2 cada vez que hay un push a `main`. Para conectarse usa las credenciales temporales de AWS Academy. No hay llaves SSH ni IPs guardadas en GitHub.

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

## 2. Preparar la EC2 (una sola vez)

En el Learner Lab, entra a **AWS Details > Download PEM** para bajar `labsuser.pem`. Luego, desde la carpeta del repo:

```bash
chmod 600 ~/Downloads/labsuser.pem
scp -i ~/Downloads/labsuser.pem deploy/preparar-ec2.sh ubuntu@<ELASTIC_IP>:~
ssh -i ~/Downloads/labsuser.pem ubuntu@<ELASTIC_IP> 'bash preparar-ec2.sh'
```

El script instala Docker y Docker Compose, agrega 1 GB de swap y crea la carpeta `~/auth-service`.

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
| `SEED_DATA` | `never` por defecto. Con `always` se cargan los usuarios de prueba (clave `123456`) |

Si quieres los usuarios de prueba, define `SEED_DATA=always` **antes del primer deploy**. Usan los ids 1, 2 y 3, así que si ya hay usuarios registrados con esos ids, no se cargan.

Las claves de MySQL, JWT y la clave interna se generan solas en la EC2 en el primer deploy (`~/auth-service/.env`) y se mantienen en los siguientes. Si quieres que el JWT sea el mismo de los otros microservicios, agrega el secreto opcional `JWT_SECRET`. Usa solo letras y números.

## 4. Desplegar

Haz un push a `main`, o entra a **Actions > CI/CD auth-service > Run workflow**. Los tres jobs son:

1. **Build y tests:** compila con Java 25, corre los tests y guarda el `.jar` y los reportes como artefactos.
2. **Imagen Docker y prueba con MySQL:** construye la imagen, la levanta con MySQL y prueba el health y el login. La imagen no se publica.
3. **Deploy en AWS EC2:** solo corre en `main`. Pasos:
   1. Configura las credenciales de AWS Academy.
   2. Busca la EC2 por su nombre.
   3. Entra con EC2 Instance Connect, usando una llave de un solo uso.
   4. Copia el jar y levanta los contenedores.
   5. Comprueba que el servicio responda desde internet.

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

1. La pestaña **Actions** con el run de `main` en verde y los tres jobs.
2. El job **Deploy en AWS EC2** abierto, con los pasos "Configurar credenciales temporales de AWS Academy" y "Verificar desde internet" en verde.
3. La sección **Artifacts** del run, con el jar y los reportes de tests.
4. La consola de EC2 con la instancia `auth-service` corriendo y su Elastic IP.
5. El navegador o Postman contra `http://<ELASTIC_IP>:8081`: el health, un registro y un login.

## Learner Lab: tener en cuenta

- **El laboratorio se apaga solo** después de unas horas, y con él la EC2. Al volver a hacer *Start Lab*, la instancia arranca y Docker levanta los contenedores solo, gracias a `restart: unless-stopped`.
- **Si el laboratorio está apagado o las credenciales caducaron**, el job de deploy falla en "Configurar credenciales" o en "Buscar la EC2". En ese caso enciende el lab, actualiza los tres secretos y vuelve a correr el workflow con **Re-run jobs**.
