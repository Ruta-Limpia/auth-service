package cl.duoc.rutalimpia.auth.exception;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import jakarta.validation.Valid;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControllerDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void recursoNoEncontradoResponde404ConFormatoComun() throws Exception {
        mockMvc.perform(get("/prueba/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.mensaje").value("Usuario 15 no existe"))
                .andExpect(jsonPath("$.path").value("/prueba/no-encontrado"));
    }

    @Test
    void reglaDeNegocioResponde409() throws Exception {
        mockMvc.perform(get("/prueba/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.mensaje").value("El email ya está registrado"));
    }

    @Test
    void bodyInvalidoResponde400ConElPrimerCampo() throws Exception {
        String body = """
                {"nombre": "", "email": "carlos@test.cl", "password": "clave1234", "rol": "CONDUCTOR"}
                """;
        mockMvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.mensaje").value("El nombre es obligatorio"));
    }

    @Test
    void jsonMalFormadoResponde400SinDetallesInternos() throws Exception {
        mockMvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON).content("{nombre:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la solicitud no es un JSON válido"));
    }

    @Test
    void rolInexistenteIndicaElCampo() throws Exception {
        String body = """
                {"nombre": "Carlos", "email": "carlos@test.cl", "password": "clave1234", "rol": "SUPERADMIN"}
                """;
        mockMvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor no válido para el campo rol"));
    }

    @Test
    void parametroConTipoIncorrectoResponde400() throws Exception {
        mockMvc.perform(get("/prueba/por-rol").param("rol", "JEFE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Valor no válido para el parámetro rol"));
    }

    @Test
    void credencialesMalasResponden401() throws Exception {
        mockMvc.perform(get("/prueba/credenciales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.mensaje").value("Email o contraseña incorrectos"));
    }

    @Test
    void accesoDenegadoResponde403() throws Exception {
        mockMvc.perform(get("/prueba/prohibido"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void metodoNoPermitidoResponde405YNo500() throws Exception {
        mockMvc.perform(delete("/prueba/conflicto"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void errorInesperadoResponde500SinFiltrarElDetalle() throws Exception {
        mockMvc.perform(get("/prueba/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.mensaje").value("Error interno del servidor"))
                .andExpect(content().string(not(containsString("jdbc"))));
    }

    @RestController
    static class ControllerDePrueba {

        @GetMapping("/prueba/no-encontrado")
        void noEncontrado() {
            throw new RecursoNoEncontradoException("Usuario 15 no existe");
        }

        @GetMapping("/prueba/conflicto")
        void conflicto() {
            throw new ReglaNegocioException("El email ya está registrado");
        }

        @PostMapping("/prueba/validar")
        void validar(@Valid @RequestBody CrearUsuarioRequest request) {
        }

        @GetMapping("/prueba/por-rol")
        void porRol(@RequestParam Rol rol) {
        }

        @GetMapping("/prueba/credenciales")
        void credenciales() {
            throw new BadCredentialsException("Email o contraseña incorrectos");
        }

        @GetMapping("/prueba/prohibido")
        void prohibido() {
            throw new AccessDeniedException("sin permiso");
        }

        @GetMapping("/prueba/error")
        void error() {
            throw new IllegalStateException("fallo la conexion jdbc:mysql://10.0.0.5/rl_auth");
        }
    }
}
