package cl.duoc.rutalimpia.auth.service;

import java.util.List;

import cl.duoc.rutalimpia.auth.dto.ActualizarUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.model.enums.Rol;

public interface UsuarioService {

    UsuarioResponse crear(CrearUsuarioRequest request);

    UsuarioResponse obtenerPorId(Long id);

    // si rol es null trae todos
    List<UsuarioResponse> listar(Rol rol);

    UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request, Long adminId);

    void eliminar(Long id, Long adminId);
}
