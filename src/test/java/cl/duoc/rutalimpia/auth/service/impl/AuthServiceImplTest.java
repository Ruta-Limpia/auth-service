package cl.duoc.rutalimpia.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.LoginRequest;
import cl.duoc.rutalimpia.auth.dto.LoginResponse;
import cl.duoc.rutalimpia.auth.dto.RegistroRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.mapper.UsuarioMapper;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;
import cl.duoc.rutalimpia.auth.security.JwtUtil;
import cl.duoc.rutalimpia.auth.service.UsuarioService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String CLAVE = "123456";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioService usuarioService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtUtil jwtUtil = new JwtUtil("rutalimpia-secreto-super-largo-de-32-chars-minimo", 60);

    private AuthServiceImpl authService;

    @BeforeEach
    void configurar() {
        authService = new AuthServiceImpl(usuarioRepository, usuarioService, passwordEncoder, jwtUtil,
                new UsuarioMapper());
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

        LoginResponse respuesta = authService.login(new LoginRequest("vecino@rutalimpia.cl", CLAVE));

        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.expiraEn()).isEqualTo(60);
        assertThat(respuesta.usuario().id()).isEqualTo(3L);
        assertThat(jwtUtil.esValido(respuesta.token())).isTrue();
        assertThat(jwtUtil.obtenerUsuarioId(respuesta.token())).isEqualTo(3L);
        assertThat(jwtUtil.obtenerRol(respuesta.token())).isEqualTo("VECINO");
    }

    @Test
    void loginIgnoraMayusculasYEspaciosEnElEmail() {
        when(usuarioRepository.findByEmail("vecino@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(3L, "vecino@rutalimpia.cl", Rol.VECINO, true)));

        LoginResponse respuesta = authService.login(new LoginRequest("  Vecino@RutaLimpia.cl ", CLAVE));

        assertThat(respuesta.usuario().email()).isEqualTo("vecino@rutalimpia.cl");
    }

    @Test
    void loginConPasswordIncorrectaLanza401() {
        when(usuarioRepository.findByEmail("vecino@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(3L, "vecino@rutalimpia.cl", Rol.VECINO, true)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("vecino@rutalimpia.cl", "otra-clave")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos");
    }

    @Test
    void loginConEmailInexistenteDaElMismoError() {
        when(usuarioRepository.findByEmail("nadie@rutalimpia.cl")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@rutalimpia.cl", CLAVE)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos");
    }

    @Test
    void usuarioDesactivadoNoPuedeIniciarSesion() {
        when(usuarioRepository.findByEmail("conductor@rutalimpia.cl"))
                .thenReturn(Optional.of(usuario(2L, "conductor@rutalimpia.cl", Rol.CONDUCTOR, false)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("conductor@rutalimpia.cl", CLAVE)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos");
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
