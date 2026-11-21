package cl.duoc.rutalimpia.auth.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import cl.duoc.rutalimpia.auth.model.enums.ResultadoAcceso;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SqsPublicadorEventosTest {

    private static final String COLA = "https://sqs.us-east-1.amazonaws.com/123456789012/rl-auth-eventos-prod";

    private final SqsClient sqs = mock(SqsClient.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void mandaElEventoComoJsonALaCola() {
        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m-1").build());
        SqsPublicadorEventos publicador = nuevoPublicador();

        publicador.publicar(EventoAuth.loginFallido(7L, "ana@test.cl", "10.0.0.5",
                ResultadoAcceso.CLAVE_INCORRECTA, 3));
        publicador.close(); // espera a que termine el envio

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(captor.capture());
        SendMessageRequest enviado = captor.getValue();
        assertThat(enviado.queueUrl()).isEqualTo(COLA);
        assertThat(enviado.messageAttributes().get("tipo").stringValue()).isEqualTo("LOGIN_FALLIDO");

        JsonNode cuerpo = jsonMapper.readTree(enviado.messageBody());
        assertThat(cuerpo.get("tipo").asString()).isEqualTo("LOGIN_FALLIDO");
        assertThat(cuerpo.get("usuarioId").asLong()).isEqualTo(7L);
        assertThat(cuerpo.get("motivo").asString()).isEqualTo("CLAVE_INCORRECTA");
        assertThat(cuerpo.get("fallidosSeguidos").asInt()).isEqualTo(3);
        assertThat(cuerpo.has("password")).isFalse();
    }

    @Test
    void siSqsFallaNoSePropagaElError() {
        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SdkClientException.create("sin conexion"));
        SqsPublicadorEventos publicador = nuevoPublicador();

        assertThatCode(() -> {
            publicador.publicar(EventoAuth.usuarioRegistrado(8L, "luis@test.cl"));
            publicador.close();
        }).doesNotThrowAnyException();
        verify(sqs).sendMessage(any(SendMessageRequest.class));
    }

    private SqsPublicadorEventos nuevoPublicador() {
        return new SqsPublicadorEventos(sqs, COLA, jsonMapper, Executors.newVirtualThreadPerTaskExecutor());
    }
}
