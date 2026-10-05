package cl.duoc.rutalimpia.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.exception.RecursoNoEncontradoException;
import cl.duoc.rutalimpia.auth.exception.ReglaNegocioException;
import cl.duoc.rutalimpia.auth.mapper.UsuarioMapper;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.Rol;
import cl.duoc.rutalimpia.auth.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    // costo 4 para que los tests no se demoren; en la app se usa el default (10)
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private UsuarioServiceImpl usuarioService;

    @BeforeEach
    void configurar() {
        usuarioService = new UsuarioServiceImpl(usuarioRepository, passwordEncoder, new UsuarioMapper());
    }

    @Test
    void creaUsuarioConEmailNormalizadoYPasswordHasheada() {
        when(usuarioRepository.existsByEmail("carlos@rutalimpia.cl")).thenReturn(false);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        UsuarioResponse respuesta = usuarioService.crear(
                new CrearUsuarioRequest("  Carlos Conductor ", " Carlos@RutaLimpia.CL ", "clave1234", Rol.CONDUCTOR));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario guardado = captor.getValue();

        assertThat(guardado.getEmail()).isEqualTo("carlos@rutalimpia.cl");
        assertThat(guardado.getNombre()).isEqualTo("Carlos Conductor");
        assertThat(guardado.getPasswordHash()).isNotEqualTo("clave1234").startsWith("$2a$");
        assertThat(passwordEncoder.matches("clave1234", guardado.getPasswordHash())).isTrue();
        assertThat(guardado.getRol()).isEqualTo(Rol.CONDUCTOR);
        assertThat(guardado.isActivo()).isTrue();

        assertThat(respuesta).isEqualTo(
                new UsuarioResponse(2L, "Carlos Conductor", "carlos@rutalimpia.cl", Rol.CONDUCTOR, true));
    }

    @Test
    void noCreaUsuarioSiElEmailYaExiste() {
        when(usuarioRepository.existsByEmail("vecino@rutalimpia.cl")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.crear(
                new CrearUsuarioRequest("Otro", "VECINO@rutalimpia.cl", "clave1234", Rol.VECINO)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El email ya está registrado");
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void registroSimultaneoConMismoEmailTerminaEn409() {
        when(usuarioRepository.existsByEmail("doble@test.cl")).thenReturn(false);
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("uk_usuarios_email"));

        assertThatThrownBy(() -> usuarioService.crear(
                new CrearUsuarioRequest("Doble", "doble@test.cl", "clave1234", Rol.VECINO)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void obtenerPorIdDevuelveElUsuario() {
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario(3L, "vecino@rutalimpia.cl", Rol.VECINO)));

        assertThat(usuarioService.obtenerPorId(3L).email()).isEqualTo("vecino@rutalimpia.cl");
    }

    @Test
    void obtenerPorIdInexistenteLanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtenerPorId(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Usuario 99 no existe");
    }

    @Test
    void listarSinRolDevuelveTodosOrdenadosPorId() {
        when(usuarioRepository.findAll(Sort.by("id"))).thenReturn(List.of(
                usuario(1L, "admin@rutalimpia.cl", Rol.ADMIN),
                usuario(2L, "conductor@rutalimpia.cl", Rol.CONDUCTOR)));

        assertThat(usuarioService.listar(null)).extracting(UsuarioResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void listarPorRolFiltra() {
        when(usuarioRepository.findByRol(Rol.CONDUCTOR))
                .thenReturn(List.of(usuario(2L, "conductor@rutalimpia.cl", Rol.CONDUCTOR)));

        assertThat(usuarioService.listar(Rol.CONDUCTOR))
                .singleElement()
                .extracting(UsuarioResponse::rol)
                .isEqualTo(Rol.CONDUCTOR);
        verify(usuarioRepository, never()).findAll(any(Sort.class));
    }

    private Usuario usuario(Long id, String email, Rol rol) {
        return Usuario.builder().id(id).nombre("Usuario " + id).email(email).passwordHash("hash").rol(rol).build();
    }
}
