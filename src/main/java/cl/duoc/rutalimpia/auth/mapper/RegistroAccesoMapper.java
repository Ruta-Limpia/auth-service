package cl.duoc.rutalimpia.auth.mapper;

import org.springframework.stereotype.Component;

import cl.duoc.rutalimpia.auth.dto.RegistroAccesoResponse;
import cl.duoc.rutalimpia.auth.model.RegistroAcceso;

@Component
public class RegistroAccesoMapper {

    public RegistroAccesoResponse toResponse(RegistroAcceso acceso) {
        return new RegistroAccesoResponse(
                acceso.getId(),
                acceso.getResultado(),
                acceso.getIp(),
                acceso.getFecha());
    }
}
