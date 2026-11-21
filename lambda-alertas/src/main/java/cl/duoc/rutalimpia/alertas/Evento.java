package cl.duoc.rutalimpia.alertas;

// lo mismo que manda auth-service (EventoAuth), leido desde el JSON del mensaje
public record Evento(
        String id,
        String tipo,
        Long usuarioId,
        String email,
        String ip,
        String motivo,
        Integer fallidosSeguidos,
        String fecha) {
}
