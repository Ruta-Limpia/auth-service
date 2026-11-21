package cl.duoc.rutalimpia.alertas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

class AlertasHandlerTest {

    private final List<String[]> enviados = new ArrayList<>();
    private final AlertasHandler handler = new AlertasHandler((asunto, mensaje) -> enviados.add(
            new String[] { asunto, mensaje }), 3);

    @Test
    void alTercerFallidoSeguidoMandaUnaAlerta() {
        SQSBatchResponse respuesta = handler.handleRequest(lote(fallido("m-1", 3)), null);

        assertEquals(1, enviados.size());
        assertEquals("RutaLimpia: 3 intentos fallidos de inicio de sesion", enviados.get(0)[0]);
        assertTrue(enviados.get(0)[1].contains("ana@test.cl"));
        assertTrue(enviados.get(0)[1].contains("10.0.0.5"));
        assertTrue(respuesta.getBatchItemFailures().isEmpty());
    }

    @Test
    void conUnoODosFallidosNoAvisa() {
        handler.handleRequest(lote(fallido("m-1", 1), fallido("m-2", 2), fallido("m-3", 4)), null);

        assertTrue(enviados.isEmpty());
    }

    @Test
    void vuelveAAvisarCadaTresFallidos() {
        handler.handleRequest(lote(fallido("m-1", 6)), null);

        assertEquals(1, enviados.size());
    }

    @Test
    void elRegistroNoGeneraAlerta() {
        SQSBatchResponse respuesta = handler.handleRequest(lote(mensaje("m-1", """
                {"id":"e-1","tipo":"USUARIO_REGISTRADO","usuarioId":9,"email":"luis@test.cl"}""")), null);

        assertTrue(enviados.isEmpty());
        assertTrue(respuesta.getBatchItemFailures().isEmpty());
    }

    @Test
    void unMensajeQueNoEsJsonSeDescartaSinReintentar() {
        SQSBatchResponse respuesta = handler.handleRequest(lote(mensaje("m-1", "esto no es json")), null);

        assertTrue(respuesta.getBatchItemFailures().isEmpty());
        assertTrue(enviados.isEmpty());
    }

    @Test
    void siSnsFallaSoloEseMensajeVuelveALaCola() {
        AlertasHandler conError = new AlertasHandler((asunto, mensaje) -> {
            throw new IllegalStateException("SNS no responde");
        }, 3);

        SQSBatchResponse respuesta = conError.handleRequest(lote(fallido("m-1", 1), fallido("m-2", 3)), null);

        assertEquals(List.of("m-2"), respuesta.getBatchItemFailures().stream()
                .map(BatchItemFailure::getItemIdentifier).toList());
    }

    @Test
    void umbralInvalidoUsaTres() {
        assertEquals(3, AlertasHandler.leerUmbral(null));
        assertEquals(3, AlertasHandler.leerUmbral("abc"));
        assertEquals(3, AlertasHandler.leerUmbral("0"));
        assertEquals(5, AlertasHandler.leerUmbral(" 5 "));
    }

    private static SQSMessage fallido(String idMensaje, int seguidos) {
        return mensaje(idMensaje, """
                {"id":"e-%s","tipo":"LOGIN_FALLIDO","usuarioId":7,"email":"ana@test.cl","ip":"10.0.0.5",
                 "motivo":"CLAVE_INCORRECTA","fallidosSeguidos":%d,"fecha":"2026-11-21T15:00:00Z"}"""
                .formatted(idMensaje, seguidos));
    }

    private static SQSMessage mensaje(String id, String cuerpo) {
        SQSMessage mensaje = new SQSMessage();
        mensaje.setMessageId(id);
        mensaje.setBody(cuerpo);
        return mensaje;
    }

    private static SQSEvent lote(SQSMessage... mensajes) {
        SQSEvent lote = new SQSEvent();
        lote.setRecords(List.of(mensajes));
        return lote;
    }
}
