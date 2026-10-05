package cl.duoc.rutalimpia.auth.service;

import java.util.List;

import cl.duoc.rutalimpia.auth.dto.CrearUsuarioRequest;
import cl.duoc.rutalimpia.auth.dto.UsuarioResponse;
import cl.duoc.rutalimpia.auth.model.enums.Rol;

public interface UsuarioService {

    UsuarioResponse crear(CrearUsuarioRequest request);

    UsuarioResponse obtenerPorId(Long id);

    // rol null devuelve todos
    List<UsuarioResponse> listar(Rol rol);
}
