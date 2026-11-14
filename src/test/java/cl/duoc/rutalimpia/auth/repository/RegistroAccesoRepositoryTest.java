package cl.duoc.rutalimpia.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import cl.duoc.rutalimpia.auth.model.RegistroAcceso;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.ResultadoAcceso;
import cl.duoc.rutalimpia.auth.model.enums.Rol;

@DataJpaTest
@ActiveProfiles("test")
class RegistroAccesoRepositoryTest {

    @Autowired
    private RegistroAccesoRepository registroAccesoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void listaLosAccesosDelMasNuevoAlMasAntiguo() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("vecino@test.cl"));
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 9, 0);
        registroAccesoRepository.save(acceso(usuario, ResultadoAcceso.CLAVE_INCORRECTA, base));
        registroAccesoRepository.save(acceso(usuario, ResultadoAcceso.EXITOSO, base.plusMinutes(5)));
        registroAccesoRepository.save(acceso(usuario, ResultadoAcceso.EXITOSO, base.plusDays(1)));

        var accesos = registroAccesoRepository.findTop20ByUsuarioIdOrderByFechaDescIdDesc(usuario.getId());

        assertThat(accesos).extracting(RegistroAcceso::getFecha)
                .containsExactly(base.plusDays(1), base.plusMinutes(5), base);
        assertThat(accesos.get(2).getResultado()).isEqualTo(ResultadoAcceso.CLAVE_INCORRECTA);
        assertThat(accesos.get(0).getUsuario().getId()).isEqualTo(usuario.getId());
    }

    @Test
    void devuelveComoMaximoVeinteYSoloDelUsuarioPedido() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("muchos@test.cl"));
        Usuario otro = usuarioRepository.save(nuevoUsuario("otro@test.cl"));
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 9, 0);
        for (int i = 0; i < 25; i++) {
            registroAccesoRepository.save(acceso(usuario, ResultadoAcceso.EXITOSO, base.plusMinutes(i)));
        }
        registroAccesoRepository.save(acceso(otro, ResultadoAcceso.EXITOSO, base.plusDays(1)));

        var accesos = registroAccesoRepository.findTop20ByUsuarioIdOrderByFechaDescIdDesc(usuario.getId());

        assertThat(accesos).hasSize(20);
        assertThat(accesos.get(0).getFecha()).isEqualTo(base.plusMinutes(24));
        assertThat(registroAccesoRepository.countByUsuarioId(usuario.getId())).isEqualTo(25);
    }

    @Test
    void laFechaSeLlenaSolaAlGuardar() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("fecha@test.cl"));

        RegistroAcceso guardado = registroAccesoRepository.saveAndFlush(acceso(usuario, ResultadoAcceso.EXITOSO, null));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getFecha()).isNotNull();
    }

    @Test
    void alBorrarElUsuarioLaBaseBorraSuHistorial() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("borrar@test.cl"));
        registroAccesoRepository.save(acceso(usuario, ResultadoAcceso.EXITOSO, null));
        registroAccesoRepository.save(acceso(usuario, ResultadoAcceso.CLAVE_INCORRECTA, null));
        entityManager.flush();
        entityManager.clear();

        usuarioRepository.deleteById(usuario.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(usuarioRepository.findById(usuario.getId())).isEmpty();
        assertThat(registroAccesoRepository.countByUsuarioId(usuario.getId())).isZero();
    }

    @Test
    void elUsuarioCargaSuHistorialPorLaRelacion() {
        Usuario usuario = usuarioRepository.save(nuevoUsuario("relacion@test.cl"));
        registroAccesoRepository.save(acceso(usuario, ResultadoAcceso.EXITOSO, null));
        registroAccesoRepository.save(acceso(usuario, ResultadoAcceso.CUENTA_INACTIVA, null));
        entityManager.flush();
        entityManager.clear();

        Usuario recargado = usuarioRepository.findById(usuario.getId()).orElseThrow();

        assertThat(recargado.getAccesos()).hasSize(2)
                .extracting(RegistroAcceso::getResultado)
                .containsExactlyInAnyOrder(ResultadoAcceso.EXITOSO, ResultadoAcceso.CUENTA_INACTIVA);
    }

    private Usuario nuevoUsuario(String email) {
        return Usuario.builder()
                .nombre("Usuario de prueba")
                .email(email)
                .passwordHash("$2a$10$hashDePrueba")
                .rol(Rol.VECINO)
                .build();
    }

    private RegistroAcceso acceso(Usuario usuario, ResultadoAcceso resultado, LocalDateTime fecha) {
        return RegistroAcceso.builder()
                .usuario(usuario)
                .resultado(resultado)
                .ip("127.0.0.1")
                .fecha(fecha)
                .build();
    }
}
