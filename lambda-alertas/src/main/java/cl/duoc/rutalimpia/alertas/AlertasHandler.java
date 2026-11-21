package cl.duoc.rutalimpia.alertas;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Lee la cola rl-auth-eventos.
// LOGIN_FALLIDO: con 3 fallidos seguidos (o 6, 9...) manda una alerta por SNS.
// USUARIO_REGISTRADO: solo queda en el log.
// Si SNS falla, ese mensaje vuelve a la cola (a la tercera va a la DLQ).
// Un mensaje que no es JSON se descarta, porque reintentarlo no lo arregla.
public class AlertasHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final int UMBRAL_POR_DEFECTO = 3;

    private final Notificador notificador;
    private final int umbral;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    // el que usa Lambda: se crea una vez por contenedor y se reutiliza entre invocaciones
    public AlertasHandler() {
        this(new SnsNotificador(System.getenv("ALERTAS_TOPIC_ARN")), leerUmbral(System.getenv("UMBRAL_FALLIDOS")));
    }

    AlertasHandler(Notificador notificador, int umbral) {
        this.notificador = notificador;
        this.umbral = umbral;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent lote, Context context) {
        List<BatchItemFailure> fallidos = new ArrayList<>();
        for (SQSMessage mensaje : lote.getRecords()) {
            try {
                procesar(json.readValue(mensaje.getBody(), Evento.class));
            } catch (JacksonException e) {
                log("mensaje %s descartado: no es un evento valido", mensaje.getMessageId());
            } catch (RuntimeException e) {
                log("mensaje %s con error, se reintenta: %s", mensaje.getMessageId(), e.getMessage());
                fallidos.add(BatchItemFailure.builder().withItemIdentifier(mensaje.getMessageId()).build());
            }
        }
        return SQSBatchResponse.builder().withBatchItemFailures(fallidos).build();
    }

    private void procesar(Evento evento) {
        switch (evento.tipo() == null ? "" : evento.tipo()) {
            case "LOGIN_FALLIDO" -> loginFallido(evento);
            case "USUARIO_REGISTRADO" -> log("usuario nuevo registrado usuarioId=%d evento=%s",
                    evento.usuarioId(), evento.id());
            default -> log("evento %s de tipo desconocido '%s', se ignora", evento.id(), evento.tipo());
        }
    }

    private void loginFallido(Evento evento) {
        int seguidos = evento.fallidosSeguidos() == null ? 0 : evento.fallidosSeguidos();
        if (seguidos < umbral || seguidos % umbral != 0) {
            log("login fallido usuarioId=%d fallidosSeguidos=%d sin alerta", evento.usuarioId(), seguidos);
            return;
        }
        // el asunto de SNS no acepta tildes
        notificador.enviar("RutaLimpia: " + seguidos + " intentos fallidos de inicio de sesion", """
                La cuenta %s (id %d) tiene %d intentos fallidos seguidos de inicio de sesión.

                Último intento: %s
                IP: %s
                Motivo: %s

                Si no fue el usuario, revisa la cuenta en RutaLimpia o desactívala.
                """.formatted(evento.email(), evento.usuarioId(), seguidos, evento.fecha(), evento.ip(),
                evento.motivo()));
        log("ALERTA enviada usuarioId=%d fallidosSeguidos=%d evento=%s", evento.usuarioId(), seguidos, evento.id());
    }

    static int leerUmbral(String valor) {
        try {
            int umbral = Integer.parseInt(valor.trim());
            return umbral > 0 ? umbral : UMBRAL_POR_DEFECTO;
        } catch (RuntimeException e) {
            return UMBRAL_POR_DEFECTO;
        }
    }

    // Lambda manda a CloudWatch todo lo que sale por consola
    private static void log(String formato, Object... datos) {
        System.out.println(formato.formatted(datos));
    }
}
