package cl.duoc.rutalimpia.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.rutalimpia.auth.model.RegistroAcceso;

public interface RegistroAccesoRepository extends JpaRepository<RegistroAcceso, Long> {

    List<RegistroAcceso> findTop20ByUsuarioIdOrderByFechaDescIdDesc(Long usuarioId);

    long countByUsuarioId(Long usuarioId);
}
