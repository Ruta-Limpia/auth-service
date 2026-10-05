package cl.duoc.rutalimpia.auth.dto;

import cl.duoc.rutalimpia.auth.model.enums.Rol;

public record UsuarioResponse(
        Long id,
        String nombre,
        String email,
        Rol rol,
        boolean activo) {
}
