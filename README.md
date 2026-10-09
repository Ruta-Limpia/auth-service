# auth-service

Microservicio de autenticación y usuarios de **RutaLimpia**. Registra usuarios, hace login y emite el JWT que validan los demás servicios (solicitudes, flota y rutas). Es el único servicio que guarda contraseñas.

## Stack

- Java 25
- Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security)
- MySQL 8
- Maven (se incluye el wrapper `mvnw`, no hace falta instalar Maven)
- JJWT 0.13.0 para los tokens
- springdoc-openapi 3.1.1 para Swagger

## Requisitos

- JDK 25 (`java -version` debe mostrar 25)
- Docker Desktop, para levantar MySQL
- Git
- Postman, para probar con la colección incluida (opcional)

## Arquitectura

El proyecto está organizado en capas:

```
cl.duoc.rutalimpia.auth
├── controller   AuthController, UsuarioController (endpoints REST)
├── service      AuthService, UsuarioService (interfaces) e impl/ (lógica de negocio)
├── repository   UsuarioRepository (Spring Data JPA)
├── model        Usuario (entidad JPA) y enums/Rol
├── dto          Requests y responses (records con Bean Validation)
├── mapper       UsuarioMapper (entidad -> response)
├── security     JwtUtil, JwtAuthFilter, InternalKeyFilter, SecurityErrorHandler
├── config       SecurityConfig, OpenApiConfig
├── validation   PasswordValida (largo de contraseña para BCrypt)
└── exception    ErrorResponse, GlobalExceptionHandler y excepciones propias
```

El controller recibe la petición y valida el body, el service aplica las reglas de negocio y el repository accede a la base. La entidad `Usuario` se mapea a la tabla `usuarios` con anotaciones JPA (`@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@Enumerated`, `@PrePersist`).

## Levantar el proyecto desde cero

### 1. Clonar el repositorio

```bash
git clone https://github.com/Ruta-Limpia/auth-service.git
cd auth-service
```

### 2. Levantar MySQL

Con Docker Desktop abierto:

```bash
docker run -d --name rl-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8
```

Esto se hace una sola vez. Las siguientes veces basta con `docker start rl-mysql`.

No hace falta crear la base: al arrancar, el servicio crea `rl_auth` (por el `createDatabaseIfNotExist=true` de la URL), JPA crea la tabla `usuarios` y `data.sql` carga los usuarios de prueba.

### 3. Compilar, probar y empaquetar con Maven

En Linux, Mac o Git Bash:

```bash
./mvnw clean          # borra la carpeta target
./mvnw test           # corre los tests (usan H2 en memoria, no necesitan MySQL)
./mvnw clean package  # compila, corre los tests y genera el .jar
./mvnw clean install  # igual que package y además lo instala en el repositorio local de Maven
```

En PowerShell o CMD se usa `mvnw.cmd` en vez de `./mvnw`.

El artefacto queda en `target/auth-service-0.0.1-SNAPSHOT.jar`.

### 4. Ejecutar el servicio

Con Maven:

```bash
./mvnw spring-boot:run
```

O con el `.jar` generado:

```bash
java -jar target/auth-service-0.0.1-SNAPSHOT.jar
```

El servicio queda en `http://localhost:8081`. Cuando aparece `Started AuthServiceApplication` en la consola está listo.

### 5. Verificar la base de datos

```bash
docker exec -it rl-mysql mysql -uroot -proot -e "SELECT id, nombre, email, rol, activo+0 AS activo FROM rl_auth.usuarios;"
```

Deben aparecer los tres usuarios de prueba.

## Configuración

La conexión y las claves están en `src/main/resources/application.properties`. Cada valor se puede cambiar con una variable de entorno; si no existe, se usa el valor de desarrollo.

| Variable                 | Valor por defecto                                 | Para qué sirve                                |
|--------------------------|---------------------------------------------------|-----------------------------------------------|
| `DB_URL`                 | `jdbc:mysql://localhost:3306/rl_auth?...`         | URL de la base de datos                       |
| `DB_USER`                | `root`                                            | Usuario de MySQL                              |
| `DB_PASSWORD`            | `root`                                            | Contraseña de MySQL                           |
| `JWT_SECRET`             | secreto de desarrollo                             | Clave HS256, igual en los 4 servicios         |
| `JWT_EXPIRATION_MINUTES` | `60`                                              | Duración del token                            |
| `INTERNAL_API_KEY`       | `rl-internal-dev`                                 | Header `X-Internal-Key` de las rutas internas |
| `SEED_DATA`              | `always`                                          | `never` para no cargar los usuarios de prueba |
| `SWAGGER_ENABLED`        | `true`                                            | `false` para apagar Swagger                   |

Las dependencias de la base están en el `pom.xml`: `spring-boot-starter-data-jpa` y `mysql-connector-j`.

## Usuarios de prueba

Los carga `data.sql` con id fijo, porque flota y rutas usan estos ids. La contraseña de los tres es `123456`.

| id | Email                     | Rol       |
|----|---------------------------|-----------|
| 1  | admin@rutalimpia.cl       | ADMIN     |
| 2  | conductor@rutalimpia.cl   | CONDUCTOR |
| 3  | vecino@rutalimpia.cl      | VECINO    |

## Endpoints

| Método | Ruta                             | Acceso         | Respuesta                     |
|--------|----------------------------------|----------------|-------------------------------|
| POST   | `/api/v1/auth/registro`          | público        | 201, siempre crea un VECINO   |
| POST   | `/api/v1/auth/login`             | público        | 200 con el token              |
| GET    | `/api/v1/usuarios/me`            | con token      | 200, perfil del token         |
| POST   | `/api/v1/usuarios`               | ADMIN          | 201, crea usuario con rol     |
| GET    | `/api/v1/usuarios?rol=CONDUCTOR` | ADMIN          | 200, lista (rol opcional)     |
| GET    | `/api/v1/usuarios/{id}`          | ADMIN          | 200 o 404                     |
| PUT    | `/api/v1/usuarios/{id}`          | ADMIN          | 200 o 404                     |
| DELETE | `/api/v1/usuarios/{id}`          | ADMIN          | 204 o 404                     |
| GET    | `/api/v1/internal/usuarios/{id}` | X-Internal-Key | 200, uso entre servicios      |

Las rutas protegidas llevan el header `Authorization: Bearer <token>`.

Los errores siempre vuelven con el mismo formato:

```json
{
  "timestamp": "2026-10-05T10:30:00",
  "status": 404,
  "error": "NOT_FOUND",
  "mensaje": "Usuario 15 no existe",
  "path": "/api/v1/usuarios/15"
}
```

| Código | Cuándo                                                      |
|--------|-------------------------------------------------------------|
| 400    | Datos inválidos (email mal escrito, contraseña corta, etc.) |
| 401    | Sin token, token inválido o credenciales incorrectas        |
| 403    | El rol no tiene permiso o falta la clave interna            |
| 404    | El usuario no existe                                        |
| 409    | Email repetido o regla de negocio                           |

## Probar el servicio

### Swagger

Con el servicio arriba, abrir `http://localhost:8081/swagger-ui.html`. Para las rutas protegidas: hacer login, copiar el `token` y pegarlo en el botón **Authorize**.

### Postman

1. Importar los dos archivos de `docs/postman/`: la colección `RutaLimpia.postman_collection.json` y el environment `RutaLimpia-local.postman_environment.json`.
2. Seleccionar el environment **RutaLimpia local** arriba a la derecha.
3. Abrir la carpeta **01 auth** y ejecutar las requests en orden, o usar **Run collection** para correrlas todas.

Los logins guardan solos los tokens en `token_vecino`, `token_conductor` y `token_admin`. Cada request tiene pruebas que validan el código HTTP y la respuesta.

## Token JWT

- Algoritmo HS256 con el secreto compartido `JWT_SECRET`.
- Claims: `sub` (id del usuario como texto), `email` y `rol`.
- Los otros servicios validan el token por su cuenta, sin llamar a auth-service.

Ejemplo del payload:

```json
{ "sub": "3", "email": "vecino@rutalimpia.cl", "rol": "VECINO", "iat": 1790600000, "exp": 1790603600 }
```
