package cl.duoc.rutalimpia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cl.duoc.rutalimpia.auth.dto.LoginRequest;
import cl.duoc.rutalimpia.auth.dto.RegistroRequest;
import cl.duoc.rutalimpia.auth.evento.EventoAuth;
import cl.duoc.rutalimpia.auth.evento.PublicadorEventos;
import cl.duoc.rutalimpia.auth.evento.TipoEvento;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;

// sin @Transactional: los eventos salen recien despues del commit, como en produccion
@SpringBootTest
@ActiveProfiles("test")
class EventosAuthIntegracionTest {

    private static final String EMAIL = "eventos@test.cl";
    private static final String CLAVE = "clave-segura";

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private PublicadorEventos publicadorEventos;

    @AfterEach
    void limpiar() {
        usuarioRepository.findByEmail(EMAIL).ifPresent(usuarioRepository::delete);
    }

    @Test
    void registroYFallidosSeguidosLleganAlPublicador() {
        Long id = authService.registrar(new RegistroRequest("Eventos", EMAIL, CLAVE)).id();
        fallar();
        fallar();
        fallar();
        // un login correcto reinicia la cuenta
        authService.login(new LoginRequest(EMAIL, CLAVE), "10.2.2.2");
        fallar();

        List<EventoAuth> eventos = eventosDe(id);
        assertThat(eventos).extracting(EventoAuth::tipo).containsExactly(
                TipoEvento.USUARIO_REGISTRADO, TipoEvento.LOGIN_FALLIDO, TipoEvento.LOGIN_FALLIDO,
                TipoEvento.LOGIN_FALLIDO, TipoEvento.LOGIN_FALLIDO);
        assertThat(eventos.subList(1, 5)).extracting(EventoAuth::fallidosSeguidos).containsExactly(1, 2, 3, 1);
    }

    private void fallar() {
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "clave-mala"), "10.2.2.2"))
                .isInstanceOf(BadCredentialsException.class);
    }

    private List<EventoAuth> eventosDe(Long usuarioId) {
        ArgumentCaptor<EventoAuth> captor = ArgumentCaptor.forClass(EventoAuth.class);
        verify(publicadorEventos, atLeastOnce()).publicar(captor.capture());
        return captor.getAllValues().stream().filter(e -> usuarioId.equals(e.usuarioId())).toList();
    }
}
