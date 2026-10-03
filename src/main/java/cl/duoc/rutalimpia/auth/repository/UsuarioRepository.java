package cl.duoc.rutalimpia.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.Rol;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Usuario> findByRol(Rol rol);
}
