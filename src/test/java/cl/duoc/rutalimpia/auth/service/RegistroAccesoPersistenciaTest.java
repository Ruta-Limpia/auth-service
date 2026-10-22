package cl.duoc.rutalimpia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;

import cl.duoc.rutalimpia.auth.dto.LoginRequest;
import cl.duoc.rutalimpia.auth.dto.RegistroRequest;
import cl.duoc.rutalimpia.auth.repository.RegistroAccesoRepository;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;

// sin @Transactional a proposito: cada llamada hace su propio commit, como en produccion
@SpringBootTest
@ActiveProfiles("test")
class RegistroAccesoPersistenciaTest {

    private static final String EMAIL = "persistencia@test.cl";

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RegistroAccesoRepository registroAccesoRepository;

    @AfterEach
    void limpiar() {
        usuarioRepository.findByEmail(EMAIL).ifPresent(usuarioRepository::delete);
    }

    @Test
    void elLoginFallidoQuedaGuardadoAunqueResponda401() {
        Long id = authService.registrar(new RegistroRequest("Persistencia", EMAIL, "clave-segura")).id();

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "otra-clave"), "10.1.1.1"))
                .isInstanceOf(BadCredentialsException.class);
        authService.login(new LoginRequest(EMAIL, "clave-segura"), "10.1.1.1");

        assertThat(registroAccesoRepository.countByUsuarioId(id)).isEqualTo(2);
    }
}
