package cl.duoc.rutalimpia.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.Rol;

@DataJpaTest
@ActiveProfiles("test")
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void guardaYRecuperaPorEmail() {
        usuarioRepository.save(nuevoUsuario("vecino@test.cl", Rol.VECINO));

        var encontrado = usuarioRepository.findByEmail("vecino@test.cl");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getId()).isNotNull();
        assertThat(encontrado.get().getRol()).isEqualTo(Rol.VECINO);
        assertThat(encontrado.get().isActivo()).isTrue();
        assertThat(encontrado.get().getFechaCreacion()).isNotNull();
    }

    @Test
    void existsByEmailDistingueRegistrados() {
        usuarioRepository.save(nuevoUsuario("conductor@test.cl", Rol.CONDUCTOR));

        assertThat(usuarioRepository.existsByEmail("conductor@test.cl")).isTrue();
        assertThat(usuarioRepository.existsByEmail("otro@test.cl")).isFalse();
    }

    @Test
    void findByRolFiltraPorRol() {
        usuarioRepository.save(nuevoUsuario("admin@test.cl", Rol.ADMIN));
        usuarioRepository.save(nuevoUsuario("c1@test.cl", Rol.CONDUCTOR));
        usuarioRepository.save(nuevoUsuario("c2@test.cl", Rol.CONDUCTOR));

        assertThat(usuarioRepository.findByRol(Rol.CONDUCTOR)).hasSize(2);
        assertThat(usuarioRepository.findByRol(Rol.ADMIN)).hasSize(1);
        assertThat(usuarioRepository.findByRol(Rol.VECINO)).isEmpty();
    }

    @Test
    void noPermiteEmailDuplicado() {
        usuarioRepository.saveAndFlush(nuevoUsuario("repetido@test.cl", Rol.VECINO));

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(nuevoUsuario("repetido@test.cl", Rol.VECINO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Usuario nuevoUsuario(String email, Rol rol) {
        return Usuario.builder()
                .nombre("Usuario de prueba")
                .email(email)
                .passwordHash("$2a$10$hashDePrueba")
                .rol(rol)
                .build();
    }
}
