package cl.duoc.rutalimpia.auth.evento;

import java.time.Instant;
import java.util.UUID;

import cl.duoc.rutalimpia.auth.model.enums.ResultadoAcceso;

// mensaje que va a la cola SQS y que lee la Lambda de alertas.
// no lleva claves ni tokens, solo lo necesario para decidir si hay que avisar.
// fallidosSeguidos cuenta este intento; ip y motivo solo vienen en LOGIN_FALLIDO
public record EventoAuth(
        String id,
        TipoEvento tipo,
        Long usuarioId,
        String email,
        String ip,
        String motivo,
        Integer fallidosSeguidos,
        String fecha) {

    public static EventoAuth loginFallido(Long usuarioId, String email, String ip,
            ResultadoAcceso motivo, int fallidosSeguidos) {
        return new EventoAuth(nuevoId(), TipoEvento.LOGIN_FALLIDO, usuarioId, email, ip,
                motivo.name(), fallidosSeguidos, ahora());
    }

    public static EventoAuth usuarioRegistrado(Long usuarioId, String email) {
        return new EventoAuth(nuevoId(), TipoEvento.USUARIO_REGISTRADO, usuarioId, email, null,
                null, null, ahora());
    }

    private static String nuevoId() {
        return UUID.randomUUID().toString();
    }

    private static String ahora() {
        return Instant.now().toString();
    }
}
