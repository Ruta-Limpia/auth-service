package cl.duoc.rutalimpia.auth.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=always")
@Transactional
class ApiIntegracionTest {

    private static final String SECRETO = "rutalimpia-secreto-super-largo-de-32-chars-minimo";
    private static final String CLAVE_INTERNA = "rl-internal-dev";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "admin@rutalimpia.cl, 1, ADMIN",
            "conductor@rutalimpia.cl, 2, CONDUCTOR",
            "vecino@rutalimpia.cl, 3, VECINO"
    })
    void usuariosSemillaPuedenIniciarSesion(String email, long id, String rol) throws Exception {
        login(email, "123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value(60))
                .andExpect(jsonPath("$.usuario.id").value(id))
                .andExpect(jsonPath("$.usuario.rol").value(rol))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist());
    }

    @Test
    void loginConClaveMalaDevuelve401ConErrorResponse() throws Exception {
        login("vecino@rutalimpia.cl", "clave-mala")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.mensaje").value("Email o contraseña incorrectos"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void registroCreaVecinoYElEmailRepetidoDa409() throws Exception {
        String body = """
                {"nombre": "Pedro Vecino", "email": "pedro@test.cl", "password": "clave-segura"}
                """;
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rol").value("VECINO"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.mensaje").value("El email ya está registrado"));
    }

    @Test
    void registroIgnoraUnRolEnviadoPorElCliente() throws Exception {
        String body = """
                {"nombre": "Colado", "email": "colado@test.cl", "password": "clave-segura", "rol": "ADMIN"}
                """;
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("VECINO"));
    }

    @Test
    void registroConDatosInvalidosDevuelve400() throws Exception {
        String body = """
                {"nombre": "Ana", "email": "no-es-email", "password": "clave-segura"}
                """;
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.mensaje").value("El email no tiene un formato válido"));
    }

    @Test
    void perfilConTokenDevuelveAlDuenoDelToken() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer("vecino@rutalimpia.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.email").value("vecino@rutalimpia.cl"));
    }

    @Test
    void perfilSinTokenOConTokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/usuarios/me"));

        mockMvc.perform(get("/api/v1/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer token.falso.123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFirmadoConOtroSecretoNoSirve() throws Exception {
        String falso = Jwts.builder()
                .subject("1")
                .claim("email", "admin@rutalimpia.cl")
                .claim("rol", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("un-secreto-distinto-de-al-menos-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + falso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenConRolDesconocidoNoSeAcepta() throws Exception {
        String conRolInterno = Jwts.builder()
                .subject("1")
                .claim("rol", "INTERNAL")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + conRolInterno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarConductoresSoloParaAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").param("rol", "CONDUCTOR")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin@rutalimpia.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(2)))
                .andExpect(jsonPath("$[*].rol").value(not(hasItem("VECINO"))));

        mockMvc.perform(get("/api/v1/usuarios").param("rol", "CONDUCTOR")
                        .header(HttpHeaders.AUTHORIZATION, bearer("vecino@rutalimpia.cl")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/v1/usuarios"));
    }

    @Test
    void adminCreaConductorYUnConductorNoPuede() throws Exception {
        String body = """
                {"nombre": "Nuevo Conductor", "email": "nuevo.conductor@test.cl", "password": "clave-segura", "rol": "CONDUCTOR"}
                """;
        mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin@rutalimpia.cl")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CONDUCTOR"));

        mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer("conductor@rutalimpia.cl")))
                .andExpect(status().isForbidden());
    }

    @Test
    void crudCompletoDeUnConductor() throws Exception {
        String admin = bearer("admin@rutalimpia.cl");
        String nuevo = """
                {"nombre": "Conductor Temporal", "email": "temporal@test.cl", "password": "clave-segura", "rol": "CONDUCTOR"}
                """;
        String creado = mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON).content(nuevo)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(creado, "$.id");

        mockMvc.perform(get("/api/v1/usuarios/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("temporal@test.cl"));

        String cambios = """
                {"nombre": "Conductor Editado", "email": "editado@test.cl", "rol": "CONDUCTOR", "activo": false}
                """;
        mockMvc.perform(put("/api/v1/usuarios/" + id).contentType(MediaType.APPLICATION_JSON).content(cambios)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Conductor Editado"))
                .andExpect(jsonPath("$.email").value("editado@test.cl"))
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(delete("/api/v1/usuarios/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/usuarios/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Usuario " + id + " no existe"));
    }

    @Test
    void usuarioDesactivadoNoPuedeIniciarSesion() throws Exception {
        String cambios = """
                {"nombre": "Carlos Conductor", "email": "conductor@rutalimpia.cl", "rol": "CONDUCTOR", "activo": false}
                """;
        mockMvc.perform(put("/api/v1/usuarios/2").contentType(MediaType.APPLICATION_JSON).content(cambios)
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin@rutalimpia.cl")))
                .andExpect(status().isOk());

        login("conductor@rutalimpia.cl", "123456").andExpect(status().isUnauthorized());
    }

    @Test
    void soloElAdminActualizaOElimina() throws Exception {
        String cambios = """
                {"nombre": "Hackeado", "email": "vecino@rutalimpia.cl", "rol": "ADMIN", "activo": true}
                """;
        mockMvc.perform(put("/api/v1/usuarios/3").contentType(MediaType.APPLICATION_JSON).content(cambios)
                        .header(HttpHeaders.AUTHORIZATION, bearer("vecino@rutalimpia.cl")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/usuarios/2").header(HttpHeaders.AUTHORIZATION, bearer("conductor@rutalimpia.cl")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdminNoPuedeEliminarseNiUsarUnEmailAjeno() throws Exception {
        String admin = bearer("admin@rutalimpia.cl");
        mockMvc.perform(delete("/api/v1/usuarios/1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("No puede eliminar su propia cuenta"));

        String emailAjeno = """
                {"nombre": "Carlos", "email": "vecino@rutalimpia.cl", "rol": "CONDUCTOR", "activo": true}
                """;
        mockMvc.perform(put("/api/v1/usuarios/2").contentType(MediaType.APPLICATION_JSON).content(emailAjeno)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
    }

    @Test
    void rutaInternaConClaveDevuelveElUsuario() throws Exception {
        mockMvc.perform(get("/api/v1/internal/usuarios/2").header("X-Internal-Key", CLAVE_INTERNA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.rol").value("CONDUCTOR"));

        mockMvc.perform(get("/api/v1/internal/usuarios/999").header("X-Internal-Key", CLAVE_INTERNA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Usuario 999 no existe"));
    }

    @Test
    void rutaInternaSinClaveOConClaveMalaDevuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/internal/usuarios/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.mensaje").value("Clave interna ausente o inválida"));

        mockMvc.perform(get("/api/v1/internal/usuarios/2").header("X-Internal-Key", "otra-clave"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/internal/usuarios/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin@rutalimpia.cl")))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerEsPublicoYListaLosEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/registro']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/me']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/internal/usuarios/{id}']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/me'].get.responses['401'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/me'].get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/UsuarioResponse"));
    }

    @Test
    void respuestasIncluyenCabecerasDeSeguridad() throws Exception {
        login("vecino@rutalimpia.cl", "123456")
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer(String email) throws Exception {
        String respuesta = login(email, "123456").andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(respuesta, "$.token");
    }
}
