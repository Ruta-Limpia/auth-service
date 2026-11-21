package cl.duoc.rutalimpia.auth.evento;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

// deja cada evento como JSON en la cola. Se envia en un hilo virtual
// para que el login no tenga que esperar la respuesta de AWS
@Slf4j
public class SqsPublicadorEventos implements PublicadorEventos, AutoCloseable {

    private final SqsClient sqs;
    private final String colaUrl;
    private final JsonMapper jsonMapper;
    private final ExecutorService envios;

    public SqsPublicadorEventos(SqsClient sqs, String colaUrl, JsonMapper jsonMapper, ExecutorService envios) {
        this.sqs = sqs;
        this.colaUrl = colaUrl;
        this.jsonMapper = jsonMapper;
        this.envios = envios;
    }

    @Override
    public void publicar(EventoAuth evento) {
        String cuerpo = jsonMapper.writeValueAsString(evento);
        envios.execute(() -> enviar(evento, cuerpo));
    }

    private void enviar(EventoAuth evento, String cuerpo) {
        try {
            String idMensaje = sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(colaUrl)
                    .messageBody(cuerpo)
                    // el tipo tambien va como atributo para poder filtrar sin leer el JSON
                    .messageAttributes(Map.of("tipo", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(evento.tipo().name())
                            .build()))
                    .build())
                    .messageId();
            log.info("Evento {} {} enviado a SQS ({})", evento.tipo(), evento.id(), idMensaje);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el evento {} {} a SQS: {}", evento.tipo(), evento.id(), e.getMessage());
        }
    }

    // al apagar el servicio se espera a que terminen los envios pendientes
    @Override
    public void close() {
        envios.close();
        sqs.close();
    }
}
