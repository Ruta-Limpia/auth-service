package cl.duoc.rutalimpia.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.LoginRequest;
import cl.duoc.rutalimpia.auth.dto.LoginResponse;
import cl.duoc.rutalimpia.auth.dto.RegistroRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.evento.EventoAuth;
import cl.duoc.rutalimpia.auth.evento.TipoEvento;
import cl.duoc.rutalimpia.auth.mapper.UsuarioMapper;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.ResultadoAcceso;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;
import cl.duoc.rutalimpia.auth.security.JwtUtil;
import cl.duoc.rutalimpia.auth.service.RegistroAccesoService;
import cl.duoc.rutalimpia.auth.service.UsuarioService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String CLAVE = "123456";
    private static final String IP = "10.0.0.5";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private RegistroAccesoService registroAccesoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtUtil jwtUtil = new JwtUtil("rutalimpia-secreto-super-largo-de-32-chars-minimo", 60);

    private AuthServiceImpl authService;

    @BeforeEach
    void configurar() {
        authService = new AuthServiceImpl(usuarioRepository, usuarioService, passwordEncoder, jwtUtil,
                new UsuarioMapper(), registroAccesoService, eventos);
    }

    @Test
    void registroSiempreCreaVecino() {
        UsuarioResponse esperado = new UsuarioResponse(10L, "Ana", "ana@test.cl", Rol.VECINO, true);
        when(usuarioService.crear(any(CrearUsuarioRequest.class))).thenReturn(esperado);

        UsuarioResponse respuesta = authService.registrar(new RegistroRequest("Ana", "ana@test.cl", "clave1234"));

        ArgumentCaptor<CrearUsuarioRequest> captor = ArgumentCaptor.forClass(CrearUsuarioRequest.class);
        verify(usuarioService).crear(captor.capture());
        assertThat(captor.getValue().rol()).isEqualTo(Rol.VECINO);
        assertThat(respuesta).isEqualTo(esperado);
    }

    @Test
    void loginCorrectoDevuelveTokenBearer() {
        when(usuarioRepository.findByEmail("vecino@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(3L, "vecino@rutalimpia.cl", Rol.VECINO, true)));

        LoginResponse respuesta = authService.login(new LoginRequest("vecino@rutalimpia.cl", CLAVE), IP);

        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.expiraEn()).isEqualTo(60);
        assertThat(respuesta.usuario().id()).isEqualTo(3L);
        assertThat(jwtUtil.esValido(respuesta.token())).isTrue();
        assertThat(jwtUtil.obtenerUsuarioId(respuesta.token())).isEqualTo(3L);
        assertThat(jwtUtil.obtenerRol(respuesta.token())).isEqualTo("VECINO");
        verify(registroAccesoService).registrar(argThat(u -> u.getId().equals(3L)), eq(ResultadoAcceso.EXITOSO), eq(IP));
    }

    @Test
    void loginIgnoraMayusculasYEspaciosEnElEmail() {
        when(usuarioRepository.findByEmail("vecino@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(3L, "vecino@rutalimpia.cl", Rol.VECINO, true)));

        LoginResponse respuesta = authService.login(new LoginRequest("  Vecino@RutaLimpia.cl ", CLAVE), IP);

        assertThat(respuesta.usuario().email()).isEqualTo("vecino@rutalimpia.cl");
    }

    @Test
    void loginConPasswordIncorrectaLanza401() {
        when(usuarioRepository.findByEmail("vecino@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(3L, "vecino@rutalimpia.cl", Rol.VECINO, true)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("vecino@rutalimpia.cl", "otra-clave"), IP))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos");
        verify(registroAccesoService).registrar(any(Usuario.class), eq(ResultadoAcceso.CLAVE_INCORRECTA), eq(IP));
    }

    @Test
    void loginConEmailInexistenteDaElMismoError() {
        when(usuarioRepository.findByEmail("nadie@rutalimpia.cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@rutalimpia.cl", CLAVE), IP))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos");
        verifyNoInteractions(registroAccesoService);
    }

    @Test
    void usuarioDesactivadoNoPuedeIniciarSesion() {
        when(usuarioRepository.findByEmail("conductor@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(2L, "conductor@rutalimpia.cl", Rol.CONDUCTOR, false)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("conductor@rutalimpia.cl", CLAVE), IP))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos");
        verify(registroAccesoService).registrar(any(Usuario.class), eq(ResultadoAcceso.CUENTA_INACTIVA), eq(IP));
    }

    @Test
    void cuentaInactivaConClaveMalaSeRegistraComoClaveIncorrecta() {
        when(usuarioRepository.findByEmail("conductor@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(2L, "conductor@rutalimpia.cl", Rol.CONDUCTOR, false)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("conductor@rutalimpia.cl", "otra-clave"), IP))
                .isInstanceOf(BadCredentialsException.class);
        verify(registroAccesoService).registrar(any(Usuario.class), eq(ResultadoAcceso.CLAVE_INCORRECTA), eq(IP));
    }

    @Test
    void registroAvisaQueHayUnUsuarioNuevo() {
        when(usuarioService.crear(any(CrearUsuarioRequest.class)))
                .thenReturn(new UsuarioResponse(10L, "Ana", "ana@test.cl", Rol.VECINO, true));

        authService.registrar(new RegistroRequest("Ana", "ana@test.cl", "clave1234"));

        EventoAuth evento = eventoPublicado();
        assertThat(evento.tipo()).isEqualTo(TipoEvento.USUARIO_REGISTRADO);
        assertThat(evento.usuarioId()).isEqualTo(10L);
        assertThat(evento.email()).isEqualTo("ana@test.cl");
    }

    @Test
    void loginFallidoAvisaConLosIntentosSeguidos() {
        when(usuarioRepository.findByEmail("vecino@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(3L, "vecino@rutalimpia.cl", Rol.VECINO, true)));
        when(registroAccesoService.contarFallidosSeguidos(3L)).thenReturn(3);

        assertThatThrownBy(() -> authService.login(new LoginRequest("vecino@rutalimpia.cl", "otra-clave"), IP))
                .isInstanceOf(BadCredentialsException.class);

        EventoAuth evento = eventoPublicado();
        assertThat(evento.tipo()).isEqualTo(TipoEvento.LOGIN_FALLIDO);
        assertThat(evento.usuarioId()).isEqualTo(3L);
        assertThat(evento.motivo()).isEqualTo("CLAVE_INCORRECTA");
        assertThat(evento.fallidosSeguidos()).isEqualTo(3);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.id()).isNotBlank();
    }

    @Test
    void loginCorrectoYEmailInexistenteNoGeneranEventos() {
        when(usuarioRepository.findByEmail("vecino@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(3L, "vecino@rutalimpia.cl", Rol.VECINO, true)));
        when(usuarioRepository.findByEmail("nadie@rutalimpia.cl")).thenReturn(Optional.empty());

        authService.login(new LoginRequest("vecino@rutalimpia.cl", CLAVE), IP);
        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@rutalimpia.cl", CLAVE), IP))
                .isInstanceOf(BadCredentialsException.class);

        verify(eventos, never()).publishEvent(any(Object.class));
    }

    private EventoAuth eventoPublicado() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(captor.capture());
        return (EventoAuth) captor.getValue();
    }

    private Usuario usuario(Long id, String email, Rol rol, boolean activo) {
        return Usuario.builder()
                .id(id)
                .nombre("Usuario " + id)
                .email(email)
                .passwordHash(passwordEncoder.encode(CLAVE))
                .rol(rol)
                .activo(activo)
                .build();
    }
}
