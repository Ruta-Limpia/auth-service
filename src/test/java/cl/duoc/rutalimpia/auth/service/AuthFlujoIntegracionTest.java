package cl.duoc.rutalimpia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.rutalimpia.auth.dto.LoginRequest;
import cl.duoc.rutalimpia.auth.dto.LoginResponse;
import cl.duoc.rutalimpia.auth.dto.RegistroRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.exception.ReglaNegocioException;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;
import cl.duoc.rutalimpia.auth.security.JwtUtil;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthFlujoIntegracionTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void registroYLoginContraLaBase() {
        UsuarioResponse registrado = authService.registrar(
                new RegistroRequest("Valentina Vecina", "Valentina@Test.cl", "clave-segura"));

        assertThat(registrado.rol()).isEqualTo(Rol.VECINO);
        assertThat(registrado.email()).isEqualTo("valentina@test.cl");
        assertThat(usuarioRepository.findByEmail("valentina@test.cl"))
                .get()
                .satisfies(u -> assertThat(u.getPasswordHash()).startsWith("$2a$10$"));

        LoginResponse login = authService.login(new LoginRequest("valentina@test.cl", "clave-segura"));

        assertThat(jwtUtil.obtenerUsuarioId(login.token())).isEqualTo(registrado.id());
        assertThat(jwtUtil.obtenerRol(login.token())).isEqualTo("VECINO");
    }

    @Test
    void noSePuedeRegistrarElMismoEmailConOtrasMayusculas() {
        authService.registrar(new RegistroRequest("Uno", "repetido@test.cl", "clave-segura"));

        assertThatThrownBy(() -> authService.registrar(new RegistroRequest("Dos", "REPETIDO@test.cl", "otra-clave")))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void loginConClaveMalaFalla() {
        authService.registrar(new RegistroRequest("Tres", "tres@test.cl", "clave-segura"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("tres@test.cl", "clave-mala")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
