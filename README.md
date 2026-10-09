# auth-service

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
model        la entidad Usuario y el enum Rol
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

No hay que crear nada en la base: cuando el servicio arranca, crea la base `rl_auth`, la tabla `usuarios` y carga tres usuarios de prueba.

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
| GET    | `/api/v1/internal/usuarios/{id}` | otros servicios     |

Para las rutas con token se agrega el header `Authorization: Bearer <token>`. La ruta interna usa el header `X-Internal-Key`.

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

## Cómo probarlo

### Con Swagger

Con el servicio corriendo, entrar a `http://localhost:8081/swagger-ui.html`. Para las rutas protegidas, primero hacer login, copiar el token y pegarlo en el botón **Authorize**.

### Con Postman

1. Importar los dos archivos de la carpeta `docs/postman`.
2. Elegir el environment **RutaLimpia local**.
3. Ejecutar las requests de la carpeta **01 auth** en orden, o usar **Run collection** para correrlas todas de una vez.

Los logins guardan el token solos, así que las demás requests ya quedan listas para usarse.
