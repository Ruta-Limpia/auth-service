package cl.duoc.rutalimpia.auth.service;

import java.util.List;

import cl.duoc.rutalimpia.auth.dto.RegistroAccesoResponse;
import cl.duoc.rutalimpia.auth.model.Usuario;
import cl.duoc.rutalimpia.auth.model.enums.ResultadoAcceso;

public interface RegistroAccesoService {

    void registrar(Usuario usuario, ResultadoAcceso resultado, String ip);

    // intentos fallidos seguidos de los ultimos 15 minutos, desde el ultimo login correcto
    int contarFallidosSeguidos(Long usuarioId);

    // los ultimos 20, del mas nuevo al mas antiguo
    List<RegistroAccesoResponse> listarPorUsuario(Long usuarioId);
}
