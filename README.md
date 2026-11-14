# auth-service

[![CI/CD auth-service](https://github.com/Ruta-Limpia/auth-service/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/Ruta-Limpia/auth-service/actions/workflows/ci-cd.yml)

Microservicio de usuarios y login de **RutaLimpia**. Se encarga de registrar usuarios, hacer el login y entregar el token (JWT) que después usan los otros servicios: solicitudes, flota y rutas.

## Qué se usó

- Java 25
- Spring Boot 4.1.1
- MySQL 8
- Maven (viene incluido con `mvnw`, no hay que instalarlo)

## Qué necesitas tener instalado

- Java 25
- Docker Desktop (para la base de datos)
- Git
- Postman (para probar)

## Cómo está organizado el código

```
controller   recibe las peticiones
service      lógica del registro, login y usuarios
repository   acceso a la base de datos
model        las entidades Usuario y RegistroAcceso, y los enums
dto          datos que entran y salen de la API
security     token y filtros de seguridad
exception    manejo de errores
```

## Cómo levantarlo

### 1. Clonar el proyecto

```bash
git clone https://github.com/Ruta-Limpia/auth-service.git
cd auth-service
```

### 2. Levantar MySQL

Con Docker Desktop abierto:

```bash
docker run -d --name rl-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8
```

Solo se hace la primera vez. Después basta con `docker start rl-mysql`.

No hay que crear nada en la base: cuando el servicio arranca, crea la base `rl_auth`, las tablas `usuarios` y `registros_acceso` y carga tres usuarios de prueba.

### 3. Compilar con Maven

```bash
./mvnw clean package
```

Esto compila, corre los tests y deja el archivo `.jar` en la carpeta `target`. En PowerShell se usa `mvnw.cmd` en vez de `./mvnw`.

Otros comandos útiles:

```bash
./mvnw clean      # borra la carpeta target
./mvnw test       # solo corre los tests
./mvnw install    # genera el jar y lo guarda en el repositorio local de Maven
```

### 4. Ejecutar

```bash
./mvnw spring-boot:run
```

O con el jar:

```bash
java -jar target/auth-service-0.0.1-SNAPSHOT.jar
```

Queda corriendo en `http://localhost:8081`. Está listo cuando aparece `Started AuthServiceApplication`.

### 5. Revisar la base de datos

```bash
docker exec -it rl-mysql mysql -uroot -proot -e "SELECT id, nombre, email, rol, activo+0 AS activo FROM rl_auth.usuarios;"
```

### Todo con Docker (opcional)

También se puede levantar auth-service junto a su MySQL con Docker Compose, sin instalar nada más:

```bash
./mvnw clean package
docker compose up -d --build
```

Para bajarlo: `docker compose down`. Si además quieres borrar los datos, usa `docker compose down -v`.

## Configuración

Los datos de conexión están en `src/main/resources/application.properties`. Por defecto usa MySQL en `localhost:3306` con usuario `root` y clave `root`.

Si se necesita cambiar algo, se puede hacer con variables de entorno: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES` e `INTERNAL_API_KEY`.

## Usuarios de prueba

Todos tienen la clave `123456`.

| id | Email                   | Rol       |
|----|-------------------------|-----------|
| 1  | admin@rutalimpia.cl     | ADMIN     |
| 2  | conductor@rutalimpia.cl | CONDUCTOR |
| 3  | vecino@rutalimpia.cl    | VECINO    |

## Endpoints

| Método | Ruta                             | Quién puede usarlo  |
|--------|----------------------------------|---------------------|
| POST   | `/api/v1/auth/registro`          | cualquiera          |
| POST   | `/api/v1/auth/login`             | cualquiera          |
| GET    | `/api/v1/usuarios/me`            | usuario con token   |
| POST   | `/api/v1/usuarios`               | ADMIN               |
| GET    | `/api/v1/usuarios`               | ADMIN               |
| GET    | `/api/v1/usuarios/{id}`          | ADMIN               |
| PUT    | `/api/v1/usuarios/{id}`          | ADMIN               |
| DELETE | `/api/v1/usuarios/{id}`          | ADMIN               |
| GET    | `/api/v1/usuarios/{id}/accesos`  | ADMIN               |
| GET    | `/api/v1/internal/usuarios/{id}` | otros servicios     |
| GET    | `/actuator/health`               | cualquiera          |

Para las rutas con token se agrega el header `Authorization: Bearer <token>`. La ruta interna usa el header `X-Internal-Key`.

## Historial de accesos

Cada intento de login de un usuario registrado queda guardado en la tabla `registros_acceso`, con el resultado (`EXITOSO`, `CLAVE_INCORRECTA` o `CUENTA_INACTIVA`), la IP y la fecha. Los intentos fallidos también se guardan, aunque la respuesta sea 401.

Es una relación uno a muchos: un `Usuario` tiene muchos `RegistroAcceso` (`@OneToMany` en `Usuario` y `@ManyToOne` en `RegistroAcceso`). La llave foránea tiene `ON DELETE CASCADE`, así que al eliminar un usuario la base borra su historial.

El admin ve los últimos 20 intentos con `GET /api/v1/usuarios/{id}/accesos`.

Cuando algo falla, la respuesta siempre trae el mismo formato:

```json
{
  "timestamp": "2026-10-05T10:30:00",
  "status": 404,
  "error": "NOT_FOUND",
  "mensaje": "Usuario 15 no existe",
  "path": "/api/v1/usuarios/15"
}
```

## CI/CD

El pipeline está en `.github/workflows/ci-cd.yml` y corre en GitHub Actions:

| Rama | Build y tests | Imagen Docker + prueba con MySQL | Deploy en AWS EC2 |
|---|---|---|---|
| PR a `develop` o `main` | sí | sí | no |
| `develop` | sí | sí | no |
| `main` | sí | sí | sí |

1. **Build y tests:** `./mvnw verify` con Java 25. Guarda el jar y los reportes de tests como artefactos.
2. **Imagen Docker:** construye la imagen con el jar ya probado, la levanta con MySQL y revisa `/actuator/health` y el login. La imagen no se publica.
3. **Deploy:** usa las credenciales temporales de AWS Academy y busca la EC2 por su nombre. Entra con EC2 Instance Connect usando una llave de un solo uso, levanta los contenedores con `deploy/desplegar.sh` y comprueba que el servicio responda desde internet.

En GitHub solo se guardan las tres credenciales de AWS Academy (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`). Las claves de MySQL y JWT se generan en la EC2 y no están en el código. Para dejar la EC2 y GitHub listos, sigue la guía [docs/despliegue-aws.md](docs/despliegue-aws.md).

`GET /actuator/health` es público y solo muestra el estado (`UP` o `DOWN`), sin detalles. El pipeline lo usa para saber si el deploy funcionó.

## Cómo probarlo

### Con Swagger

Con el servicio corriendo, entrar a `http://localhost:8081/swagger-ui.html`. Para las rutas protegidas, primero hacer login, copiar el token y pegarlo en el botón **Authorize**.

### Con Postman

1. Importar los dos archivos de la carpeta `docs/postman`.
2. Elegir el environment **RutaLimpia local**.
3. Ejecutar las requests de la carpeta **01 auth** en orden, o usar **Run collection** para correrlas todas de una vez.

Los logins guardan el token solos, así que las demás requests ya quedan listas para usarse.
