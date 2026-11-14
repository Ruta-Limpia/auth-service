package cl.duoc.rutalimpia.auth.dto;

import java.time.LocalDateTime;

import cl.duoc.rutalimpia.auth.model.enums.ResultadoAcceso;

public record RegistroAccesoResponse(
        Long id,
        ResultadoAcceso resultado,
        String ip,
        LocalDateTime fecha) {
}
